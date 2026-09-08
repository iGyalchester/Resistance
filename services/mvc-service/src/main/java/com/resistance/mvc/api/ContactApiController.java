package com.resistance.mvc.api;

import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The caller's address book. Same shape and same 404 rule as the
 * applications API; the application count per contact is derived from
 * the caller's own applications, so it can never reveal another user's.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactApiController {

    private final ContactService contactService;
    private final JobApplicationService applicationService;

    public ContactApiController(ContactService contactService, JobApplicationService applicationService) {
        this.contactService = contactService;
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<Object> list(HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        Map<Integer, Long> counts = counts(accountId);
        return ResponseEntity.ok(contactService.findAllForOwner(accountId).stream()
                .map(c -> ContactView.of(c, counts.getOrDefault(c.getId(), 0L)))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable("id") int id, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        return ResponseEntity.ok(view(id, accountId));
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody ContactRequest body, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        Contact contact = new Contact();
        apply(body, contact);
        contactService.saveForOwner(contact, accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(contact.getId(), accountId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable("id") int id,
                                         @Valid @RequestBody ContactRequest body, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        Contact contact = contactService.findByIdForOwner(id, accountId).orElseThrow(NotFoundException::new);
        apply(body, contact);
        contactService.saveForOwner(contact, accountId);
        return ResponseEntity.ok(view(id, accountId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable("id") int id, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        if (!contactService.deleteByIdForOwner(id, accountId)) {
            throw new NotFoundException();
        }
        // applications keep their row; the database sets their contact to null
        return ResponseEntity.noContent().build();
    }

    private static void apply(ContactRequest body, Contact contact) {
        contact.setFirstName(body.firstName().trim());
        contact.setLastName(blankToNull(body.lastName()));
        contact.setEmail(blankToNull(body.email()));
    }

    private ContactView view(int id, int accountId) {
        Contact contact = contactService.findByIdForOwner(id, accountId).orElseThrow(NotFoundException::new);
        return ContactView.of(contact, counts(accountId).getOrDefault(id, 0L));
    }

    private Map<Integer, Long> counts(int accountId) {
        return applicationService.findAllForOwner(accountId).stream()
                .map(JobApplication::getContact)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Contact::getId, Collectors.counting()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
