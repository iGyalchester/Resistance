package com.resistance.mvc.api;

import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Applications for the React app. Owner-scoping lives in the services -
 * this controller only supplies the session account id, exactly like the
 * Thymeleaf controllers do, and turns "not yours" into a 404 that is
 * indistinguishable from "does not exist".
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationApiController {

    private final JobApplicationService applicationService;
    private final ContactService contactService;

    public ApplicationApiController(JobApplicationService applicationService,
                                    ContactService contactService) {
        this.applicationService = applicationService;
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(name = "status", required = false) String status,
                                       @RequestParam(name = "q", required = false) String q,
                                       HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        boolean filterByStatus = status != null && !status.isBlank();
        Optional<ApplicationStatus> wanted = filterByStatus ? ApplicationStatus.fromString(status) : Optional.empty();
        if (filterByStatus && wanted.isEmpty()) {
            // a status name that is not one of ours matches nothing, never everything
            return ResponseEntity.ok(List.of());
        }
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<ApplicationView> views = applicationService.findAllForOwner(accountId).stream()
                .filter(app -> wanted.isEmpty() || wanted.get() == app.getStatus())
                .filter(app -> needle.isEmpty() || contains(app.getCompanyName(), needle)
                        || contains(app.getPositionTitle(), needle))
                .map(ApplicationView::of)
                .toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable("id") int id, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        return ResponseEntity.ok(detail(id, accountId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<Object> history(@PathVariable("id") int id, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        List<StatusHistory> history = applicationService.historyForOwner(id, accountId)
                .orElseThrow(NotFoundException::new);
        return ResponseEntity.ok(history.stream().map(StatusChangeView::of).toList());
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody ApplicationRequest body, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        JobApplication application = new JobApplication();
        apply(body, application, accountId);
        if (body.appliedOn() != null) {
            application.setAppliedAt(body.appliedOn().atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        applicationService.saveForOwner(application, accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(application.getId(), accountId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable("id") int id,
                                         @Valid @RequestBody ApplicationRequest body, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        JobApplication application = applicationService.findByIdForOwner(id, accountId)
                .orElseThrow(NotFoundException::new);
        apply(body, application, accountId);
        applicationService.saveForOwner(application, accountId);
        return ResponseEntity.ok(detail(id, accountId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable("id") int id, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        if (!applicationService.deleteByIdForOwner(id, accountId)) {
            throw new NotFoundException();
        }
        return ResponseEntity.noContent().build();
    }

    private void apply(ApplicationRequest body, JobApplication application, int accountId) {
        application.setCompanyName(body.companyName().trim());
        application.setPositionTitle(blankToNull(body.positionTitle()));
        application.setStatus(body.status());
        // a contact id the caller does not own is a 404, same as any other foreign row
        Contact contact = body.contactId() == null ? null
                : contactService.findByIdForOwner(body.contactId(), accountId).orElseThrow(NotFoundException::new);
        application.setContact(contact);
    }

    private ApplicationDetailView detail(int id, int accountId) {
        JobApplication application = applicationService.findByIdForOwner(id, accountId)
                .orElseThrow(NotFoundException::new);
        List<StatusHistory> history = applicationService.historyForOwner(id, accountId).orElse(List.of());
        return ApplicationDetailView.of(application, history);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
