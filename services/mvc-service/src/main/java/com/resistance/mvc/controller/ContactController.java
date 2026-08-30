package com.resistance.mvc.controller;

import com.resistance.mvc.dao.ContactRepository;
import com.resistance.shared.models.entity.Contact;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/contacts")
public class ContactController {

	private ContactRepository contactRepository;

	public ContactController(ContactRepository theContactRepository) {
		contactRepository = theContactRepository;
	}

	@GetMapping("/list")
	public String listContacts(Model theModel) {

		List<Contact> theContacts = contactRepository.findAll();

		theModel.addAttribute("contacts", theContacts);

		return "contacts/list-contacts";
	}

	@GetMapping("/showFormForAdd")
	public String showFormForAdd(Model theModel) {

		theModel.addAttribute("contact", new Contact());

		return "contacts/contact-form";
	}

	@GetMapping("/showFormForUpdate")
	public String showFormForUpdate(@RequestParam("contactId") int theId,
									Model theModel) {

		Contact theContact = contactRepository.findById(theId)
				.orElseThrow(() -> new RuntimeException("Contact id not found - " + theId));

		theModel.addAttribute("contact", theContact);

		return "contacts/contact-form";
	}

	@PostMapping("/save")
	public String saveContact(@ModelAttribute("contact") Contact theContact) {

		contactRepository.save(theContact);

		return "redirect:/contacts/list";
	}

	@PostMapping("/delete")
	public String delete(@RequestParam("contactId") int theId) {

		contactRepository.deleteById(theId);

		return "redirect:/contacts/list";
	}
}
