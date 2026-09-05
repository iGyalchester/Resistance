package com.resistance.mvc.controller;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.service.ContactService;
import com.resistance.shared.models.entity.Contact;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Owner-scoped contact CRUD: every operation runs as the logged-in account
 * (Spring Security guarantees one exists here), and the service layer
 * refuses cross-account access. Same shape as
 * {@link JobApplicationController} on purpose.
 */
@Controller
@RequestMapping("/contacts")
public class ContactController {

	private ContactService contactService;

	public ContactController(ContactService theContactService) {
		contactService = theContactService;
	}

	private int accountId(HttpSession session) {
		return (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);
	}

	@GetMapping("/list")
	public String listContacts(HttpSession session, Model theModel) {

		theModel.addAttribute("contacts", contactService.findAllForOwner(accountId(session)));

		return "contacts/list-contacts";
	}

	@GetMapping("/showFormForAdd")
	public String showFormForAdd(Model theModel) {

		theModel.addAttribute("contact", new Contact());

		return "contacts/contact-form";
	}

	@GetMapping("/showFormForUpdate")
	public String showFormForUpdate(@RequestParam("contactId") int theId,
									HttpSession session, Model theModel) {

		Contact theContact = contactService.findByIdForOwner(theId, accountId(session))
				.orElse(null);
		if (theContact == null) {
			// not yours (or gone) - back to your list, no information leaked
			return "redirect:/contacts/list";
		}

		theModel.addAttribute("contact", theContact);

		return "contacts/contact-form";
	}

	@PostMapping("/save")
	public String saveContact(@ModelAttribute("contact") Contact theContact,
							  HttpSession session) {

		contactService.saveForOwner(theContact, accountId(session));

		return "redirect:/contacts/list";
	}

	@PostMapping("/delete")
	public String delete(@RequestParam("contactId") int theId, HttpSession session) {

		contactService.deleteByIdForOwner(theId, accountId(session));

		return "redirect:/contacts/list";
	}
}
