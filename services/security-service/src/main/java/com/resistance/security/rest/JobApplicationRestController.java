package com.resistance.security.rest;

import com.resistance.shared.exceptions.JobApplicationNotFoundException;
import tools.jackson.databind.json.JsonMapper;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.security.service.JobApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JobApplicationRestController {

    private JobApplicationService applicationService;

    private JsonMapper jsonMapper;

    @Autowired
    public JobApplicationRestController(JobApplicationService theJobApplicationService, JsonMapper theJsonMapper) {
        applicationService = theJobApplicationService;
        jsonMapper = theJsonMapper;
    }

    // expose "/applications" and return a list of applications
    @GetMapping("/applications")
    public List<JobApplication> findAll() {
        return applicationService.findAll();
    }

    // add mapping for GET /applications/{applicationId}

    @GetMapping("/applications/{applicationId}")
    public JobApplication getJobApplication(@PathVariable int applicationId) {

        JobApplication theJobApplication = applicationService.findById(applicationId);

        if (theJobApplication == null) {
            throw new JobApplicationNotFoundException(applicationId);
        }

        return theJobApplication;
    }

    // add mapping for POST /applications - add new application

    @PostMapping("/applications")
    public JobApplication addJobApplication(@RequestBody JobApplication theJobApplication) {

        // also just in case they pass an id in JSON ... set id to 0
        // this is to force a save of new item ... instead of update

        theJobApplication.setId(0);

        JobApplication dbJobApplication = applicationService.save(theJobApplication);

        return dbJobApplication;
    }

    // add mapping for PUT /applications - update existing application

    @PutMapping("/applications")
    public JobApplication updateJobApplication(@RequestBody JobApplication theJobApplication) {

        JobApplication dbJobApplication = applicationService.save(theJobApplication);

        return dbJobApplication;
    }

    // add mapping for PATCH /applications/{applicationId} - patch application ... partial
    // update

    @PatchMapping("/applications/{applicationId}")
    public JobApplication patchJobApplication(@PathVariable int applicationId,
            @RequestBody Map<String, Object> patchPayload) {

        // Step 1: Retrieve the existing application from database
        JobApplication tempJobApplication = applicationService.findById(applicationId);

        if (tempJobApplication == null) {
            throw new JobApplicationNotFoundException(applicationId);
        }

        // Step 2: Security check - prevent ID modifications
        // The ID should never change, so reject any attempts to modify it
        if (patchPayload.containsKey("id")) {
            throw new RuntimeException(
                    "JobApplication id cannot be modified. Remove 'id' from request body.");
        }

        // Step 3: Apply the partial update
        // This creates a NEW application object with the updates applied
        JobApplication patchedJobApplication = jsonMapper.updateValue(tempJobApplication, patchPayload);

        // Step 4: Save the updated application to database and return it
        JobApplication dbJobApplication = applicationService.save(patchedJobApplication);

        return dbJobApplication;
    }

    // add mapping for DELETE /applications/{applicationId} - delete application

    @DeleteMapping("/applications/{applicationId}")
    public String deleteJobApplication(@PathVariable int applicationId) {

        JobApplication tempJobApplication = applicationService.findById(applicationId);

        // throw exception if null

        if (tempJobApplication == null) {
            throw new JobApplicationNotFoundException(applicationId);
        }

        applicationService.deleteById(applicationId);

        return "Deleted application id - " + applicationId;
    }

}
