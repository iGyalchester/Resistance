package com.resistance.mvc.api;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationApiControllerTests {

    private JobApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(JobApplicationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationApiController(applicationService))
                .build();
    }

    @Test
    void listReturnsOnlyTheSessionOwnersApplicationsAsFlatViews() throws Exception {
        JobApplication acme = new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.INTERVIEW);
        acme.setId(1);
        acme.setContact(new Contact("Dana", "Reyes", "dana.reyes@acme.com"));
        JobApplication globex = new JobApplication("Globex", null, ApplicationStatus.APPLIED);
        globex.setId(2);
        when(applicationService.findAllForOwner(7)).thenReturn(List.of(acme, globex));

        mockMvc.perform(get("/api/applications").sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].status").value("INTERVIEW"))
                .andExpect(jsonPath("$[0].contactName").value("Dana Reyes"))
                .andExpect(jsonPath("$[1].positionTitle").doesNotExist())
                .andExpect(jsonPath("$[1].contactName").doesNotExist());

        // the owner id came from the session, nowhere else
        verify(applicationService).findAllForOwner(7);
    }

    @Test
    void listWithoutSessionIs401AndNeverTouchesTheService() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));

        verify(applicationService, never()).findAllForOwner(anyInt());
    }
}
