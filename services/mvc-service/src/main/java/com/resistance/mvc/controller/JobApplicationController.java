package com.resistance.mvc.controller;

import java.util.List;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;

/**
 * Owner-scoped application CRUD: every operation runs as the logged-in
 * account (Spring Security guarantees one exists here), and the service
 * layer refuses cross-account access.
 */
@Controller
@RequestMapping("/applications")
public class JobApplicationController {

	private JobApplicationService applicationService;

	private ContactService contactService;

	public JobApplicationController(JobApplicationService theJobApplicationService,
									ContactService theContactService) {
		applicationService = theJobApplicationService;
		contactService = theContactService;
	}

	private int accountId(HttpSession session) {
		return (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);
	}

	// expose the status choices to the form's dropdown
	@ModelAttribute("statuses")
	public ApplicationStatus[] statuses() {
		return ApplicationStatus.values();
	}

	// expose the contacts to the form's dropdown, fresh from the db each
	// request - only the signed-in user's, so the dropdown cannot even name
	// somebody else's recruiter
	@ModelAttribute("contacts")
	public List<Contact> contacts(HttpSession session) {
		return contactService.findAllForOwner(accountId(session));
	}

	@GetMapping("/list")
	public String listApplications(HttpSession session, Model theModel) {

		theModel.addAttribute("applications", applicationService.findAllForOwner(accountId(session)));

		return "applications/list-applications";
	}

	@GetMapping("/showFormForAdd")
	public String showFormForAdd(Model theModel) {

		theModel.addAttribute("application", new JobApplication());

		return "applications/application-form";
	}

	@GetMapping("/showFormForUpdate")
	public String showFormForUpdate(@RequestParam("applicationId") int theId,
									HttpSession session, Model theModel) {

		JobApplication theApplication =
				applicationService.findByIdForOwner(theId, accountId(session))
						.orElse(null);
		if (theApplication == null) {
			// not yours (or gone) - back to your list, no information leaked
			return "redirect:/applications/list";
		}

		theModel.addAttribute("application", theApplication);

		return "applications/application-form";
	}

	@PostMapping("/save")
	public String saveApplication(@ModelAttribute("application") JobApplication theApplication,
								  HttpSession session) {

		applicationService.saveForOwner(theApplication, accountId(session));

		return "redirect:/applications/list";
	}

	@PostMapping("/delete")
	public String delete(@RequestParam("applicationId") int theId, HttpSession session) {

		applicationService.deleteByIdForOwner(theId, accountId(session));

		return "redirect:/applications/list";
	}
}
