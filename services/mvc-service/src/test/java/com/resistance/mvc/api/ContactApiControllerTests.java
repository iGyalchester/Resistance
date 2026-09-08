package com.resistance.mvc.api;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

class ContactApiControllerTests {

    private static final int ME = 7;

    private ContactService contactService;
    private JobApplicationService applicationService;
    private MockMvc mockMvc;
    private Contact dana;
    private Contact marcus;

    @BeforeEach
    void setUp() {
        contactService = mock(ContactService.class);
        applicationService = mock(JobApplicationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContactApiController(contactService, applicationService))
                .setControllerAdvice(new ApiErrorHandler())
                .build();

        UserAccount me = new UserAccount("Boris", "boris@gmail.com");
        me.setId(ME);
        dana = new Contact("Dana", "Reyes", "dana.reyes@acme.com", me);
        dana.setId(3);
        marcus = new Contact("Marcus", "Lee", null, me);
        marcus.setId(4);
        when(contactService.findAllForOwner(ME)).thenReturn(List.of(marcus, dana));
        when(contactService.findByIdForOwner(3, ME)).thenReturn(Optional.of(dana));

        JobApplication a = new JobApplication("Acme", "Eng", ApplicationStatus.APPLIED);
        a.setContact(dana);
        JobApplication b = new JobApplication("Acme", "Staff Eng", ApplicationStatus.OFFER);
        b.setContact(dana);
        JobApplication c = new JobApplication("Globex", null, ApplicationStatus.APPLIED);
        when(applicationService.findAllForOwner(ME)).thenReturn(List.of(a, b, c));
    }

    @Test
    void listCountsTheOwnersApplicationsPerContact() throws Exception {
        mockMvc.perform(get("/api/contacts").sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("Marcus"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].applicationCount").value(0))
                .andExpect(jsonPath("$[1].firstName").value("Dana"))
                .andExpect(jsonPath("$[1].applicationCount").value(2));
    }

    @Test
    void anonymousCallsAre401() throws Exception {
        mockMvc.perform(get("/api/contacts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Dana\"}"))
                .andExpect(status().isUnauthorized());
        verify(contactService, never()).saveForOwner(any(), anyInt());
    }

    @Test
    void createTrimsAndSavesForTheSessionOwner() throws Exception {
        doAnswer(inv -> {
            Contact saved = inv.getArgument(0);
            saved.setId(9);
            when(contactService.findByIdForOwner(9, ME)).thenReturn(Optional.of(saved));
            return null;
        }).when(contactService).saveForOwner(any(Contact.class), eq(ME));

        mockMvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\" Sam \",\"lastName\":\"\",\"email\":\"sam@initech.example\"}")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.firstName").value("Sam"))
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.applicationCount").value(0));

        ArgumentCaptor<Contact> saved = ArgumentCaptor.forClass(Contact.class);
        verify(contactService).saveForOwner(saved.capture(), eq(ME));
        assertEquals("Sam", saved.getValue().getFirstName());
        assertNull(saved.getValue().getLastName());
    }

    @Test
    void invalidContactIs400WithFieldNames() throws Exception {
        mockMvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"email\":\"not-an-email\"}")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.firstName").value("required"))
                .andExpect(jsonPath("$.fields.email").value("not an email address"));

        verify(contactService, never()).saveForOwner(any(), anyInt());
    }

    @Test
    void updateAndDeleteOfAForeignContactAre404() throws Exception {
        when(contactService.findByIdForOwner(42, ME)).thenReturn(Optional.empty());
        when(contactService.deleteByIdForOwner(42, ME)).thenReturn(false);

        mockMvc.perform(put("/api/contacts/42").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"X\"}")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mockMvc.perform(delete("/api/contacts/42").sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNotFound());

        verify(contactService, never()).saveForOwner(any(), anyInt());
    }

    @Test
    void updateAndDeleteOfAnOwnedContactGoThroughTheService() throws Exception {
        when(contactService.deleteByIdForOwner(3, ME)).thenReturn(true);

        mockMvc.perform(put("/api/contacts/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Dana\",\"lastName\":\"Reyes-Ortiz\",\"email\":\"dana@acme.com\"}")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Reyes-Ortiz"))
                .andExpect(jsonPath("$.applicationCount").value(2));
        verify(contactService).saveForOwner(dana, ME);

        mockMvc.perform(delete("/api/contacts/3").sessionAttr(LoginController.SESSION_ACCOUNT_ID, ME))
                .andExpect(status().isNoContent());
        verify(contactService).deleteByIdForOwner(3, ME);
    }
}
