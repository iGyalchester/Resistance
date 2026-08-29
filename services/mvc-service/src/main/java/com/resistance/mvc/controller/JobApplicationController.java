package com.resistance.mvc.controller;

import java.util.List;

import com.resistance.mvc.service.JobApplicationService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.resistance.shared.models.entity.JobApplication;

@Controller
@RequestMapping("/applications")
public class JobApplicationController {

	private JobApplicationService applicationService;

	public JobApplicationController(JobApplicationService theJobApplicationService) {
		applicationService = theJobApplicationService;
	}

	// add mapping for "/list"

	@GetMapping("/list")
	public String listApplications(Model theModel) {

		// get the applications from db
		List<JobApplication> theApplications = applicationService.findAll();

		// add to the spring model
		theModel.addAttribute("applications", theApplications);

		return "applications/list-applications";
	}

	@GetMapping("/showFormForAdd")
	public String showFormForAdd(Model theModel) {

		// create model attribute to bind form data
		JobApplication theJobApplication = new JobApplication();

		theModel.addAttribute("application", theJobApplication);

		return "applications/application-form";
	}

	@GetMapping("/showFormForUpdate")
	public String showFormForUpdate(@RequestParam("applicationId") int theId,
									Model theModel) {

		// get the application from the service
		JobApplication theJobApplication = applicationService.findById(theId);

		// set application as a model attribute to pre-populate the form
		theModel.addAttribute("application", theJobApplication);

		// send over to our form
		return "applications/application-form";
	}

	@PostMapping("/save")
	public String saveJobApplication(@ModelAttribute("application") JobApplication theJobApplication) {

		// save the application
		applicationService.save(theJobApplication);

		// use a redirect to prevent duplicate submissions
		return "redirect:/applications/list";
	}

	@GetMapping("/delete")
	public String delete(@RequestParam("applicationId") int theId) {

		// delete the application
		applicationService.deleteById(theId);

		// redirect to /applications/list
		return "redirect:/applications/list";

	}
}









