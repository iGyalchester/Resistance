package com.resistance.mvc.api;

import com.resistance.mvc.auth.SessionAuthenticator;
import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationApiControllerTests {

    private static final int ME = 7;

    private JobApplicationService applicationService;
    private ContactService contactService;
    private MockMvc mockMvc;

    private JobApplication acme;
    private JobApplication globex;
    private Contact dana;

    @BeforeEach
    void setUp() {
        applicationService = mock(JobApplicationService.class);
        contactService = mock(ContactService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationApiController(applicationService, contactService))
                .setControllerAdvice(new ApiErrorHandler())
                .build();

        UserAccount me = new UserAccount("Boris", "boris@gmail.com");
        me.setId(ME);
        dana = new Contact("Dana", "Reyes", "dana.reyes@acme.com", me);
        dana.setId(3);

        acme = new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.INTERVIEW);
        acme.setId(1);
        acme.setContact(dana);
        acme.setOwner(me);
        globex = new JobApplication("Globex", null, ApplicationStatus.APPLIED);
        globex.setId(2);
        globex.setOwner(me);
        when(applicationService.findAllForOwner(ME)).thenReturn(List.of(acme, globex));
        when(applicationService.findByIdForOwner(1, ME)).thenReturn(Optional.of(acme));
        when(applicationService.historyForOwner(1, ME)).thenReturn(Optional.of(List.of(
                new StatusHistory(acme, null, ApplicationStatus.APPLIED,
                        Instant.parse("2026-08-20T09:00:00Z"), StatusHistory.SOURCE_INTAKE),
                new StatusHistory(acme, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW,
                        Instant.parse("2026-08-26T11:00:00Z"), StatusHistory.SOURCE_MANUAL))));
        when(contactService.findByIdForOwner(3, ME)).thenReturn(Optional.of(dana));
    }

    @Test
    void listReturnsOnlyTheSessionOwnersApplicationsAsFlatViews() throws Exception {
        mockMvc.perform(get("/api/applications").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].status").value("INTERVIEW"))
                .andExpect(jsonPath("$[0].contactName").value("Dana Reyes"))
                .andExpect(jsonPath("$[0].contactId").value(3))
                .andExpect(jsonPath("$[1].contactId").doesNotExist())
                .andExpect(jsonPath("$[1].positionTitle").doesNotExist())
                .andExpect(jsonPath("$[1].contactName").doesNotExist());

        // the owner id came from the session, nowhere else
        verify(applicationService).findAllForOwner(ME);
    }

    @Test
    void listFiltersByStatusAndSearchText() throws Exception {
        mockMvc.perform(get("/api/applications").param("status", "applied")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value("Globex"));

        mockMvc.perform(get("/api/applications").param("q", "backend")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyName").value("Acme Corp"));

        // an unknown status name matches nothing rather than everything
        mockMvc.perform(get("/api/applications").param("status", "bogus")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listWithoutSessionIs401AndNeverTouchesTheService() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));

        verify(applicationService, never()).findAllForOwner(anyInt());
    }

    @Test
    void detailCarriesTheContactIdAndTheTimelineOldestFirst() throws Exception {
        mockMvc.perform(get("/api/applications/1").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.contactId").value(3))
                .andExpect(jsonPath("$.contactName").value("Dana Reyes"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.history[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.history[0].source").value("INTAKE"))
                .andExpect(jsonPath("$.history[1].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$.history[1].toStatus").value("INTERVIEW"))
                .andExpect(jsonPath("$.history[1].changedAt").value("2026-08-26T11:00:00Z"));
    }

    @Test
    void someoneElsesApplicationIs404OnEveryRoute() throws Exception {
        // the service answers empty for a foreign id; nothing else is consulted
        when(applicationService.findByIdForOwner(42, ME)).thenReturn(Optional.empty());
        when(applicationService.historyForOwner(42, ME)).thenReturn(Optional.empty());
        when(applicationService.deleteByIdForOwner(42, ME)).thenReturn(false);

        mockMvc.perform(get("/api/applications/42").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mockMvc.perform(get("/api/applications/42/history").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/applications/42").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"X\",\"status\":\"APPLIED\"}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/applications/42").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound());

        verify(applicationService, never()).saveForOwner(any(), anyInt());
    }

    @Test
    void createSavesForTheSessionOwnerAndAnswers201WithTheDetail() throws Exception {
        doAnswer(inv -> {
            JobApplication saved = inv.getArgument(0);
            saved.setId(5);
            when(applicationService.findByIdForOwner(5, ME)).thenReturn(Optional.of(saved));
            when(applicationService.historyForOwner(5, ME)).thenReturn(Optional.of(List.of(
                    new StatusHistory(saved, null, saved.getStatus(),
                            Instant.parse("2026-09-01T10:00:00Z"), StatusHistory.SOURCE_MANUAL))));
            return null;
        }).when(applicationService).saveForOwner(any(JobApplication.class), eq(ME));

        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"  Initech \",\"positionTitle\":\"\",\"status\":\"SCREENING\","
                                + "\"contactId\":3,\"appliedOn\":\"2026-08-12\"}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.companyName").value("Initech"))
                .andExpect(jsonPath("$.positionTitle").doesNotExist())
                .andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.appliedOn").value("2026-08-12"))
                .andExpect(jsonPath("$.contactId").value(3))
                .andExpect(jsonPath("$.history[0].toStatus").value("SCREENING"));

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(applicationService).saveForOwner(saved.capture(), eq(ME));
        assertEquals("Initech", saved.getValue().getCompanyName());
        assertNull(saved.getValue().getPositionTitle());
        assertEquals(dana, saved.getValue().getContact());
        assertEquals(Instant.parse("2026-08-12T00:00:00Z"), saved.getValue().getAppliedAt());
    }

    @Test
    void createWithSomeoneElsesContactIs404AndSavesNothing() throws Exception {
        when(contactService.findByIdForOwner(99, ME)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Initech\",\"status\":\"APPLIED\",\"contactId\":99}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound());

        verify(applicationService, never()).saveForOwner(any(), anyInt());
    }

    @Test
    void invalidBodiesAre400WithTheOffendingFields() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"   \",\"positionTitle\":\"" + "x".repeat(91) + "\"}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.companyName").value("required"))
                .andExpect(jsonPath("$.fields.positionTitle").value("at most 90 characters"))
                .andExpect(jsonPath("$.fields.status").value("required"));

        // a status name that is not one of ours never reaches validation
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Initech\",\"status\":\"HIRED\"}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));

        verify(applicationService, never()).saveForOwner(any(), anyInt());
    }

    @Test
    void updateChangesFieldsOnTheOwnedRowAndSavesThroughTheService() throws Exception {
        mockMvc.perform(put("/api/applications/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Acme Corp\",\"positionTitle\":\"Staff Engineer\","
                                + "\"status\":\"OFFER\",\"contactId\":null}")
                        .sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER"))
                .andExpect(jsonPath("$.positionTitle").value("Staff Engineer"))
                .andExpect(jsonPath("$.contactId").doesNotExist());

        verify(applicationService).saveForOwner(acme, ME);
        assertEquals(ApplicationStatus.OFFER, acme.getStatus());
        assertNull(acme.getContact());
    }

    @Test
    void deleteAnswers204ForAnOwnedRow() throws Exception {
        when(applicationService.deleteByIdForOwner(1, ME)).thenReturn(true);

        mockMvc.perform(delete("/api/applications/1").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNoContent());
    }

    @Test
    void writesWithoutSessionAre401() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Initech\",\"status\":\"APPLIED\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/applications/1"))
                .andExpect(status().isUnauthorized());

        verify(applicationService, never()).saveForOwner(any(), anyInt());
        verify(applicationService, never()).deleteByIdForOwner(anyInt(), anyInt());
    }

    @Test
    void nonNumericIdIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/applications/abc").sessionAttr(SessionAuthenticator.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }
}
