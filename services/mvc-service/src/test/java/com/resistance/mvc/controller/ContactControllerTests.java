package com.resistance.mvc.controller;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.service.ContactService;
import com.resistance.shared.models.entity.Contact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * On the tenancy question the controller has one job: pass the session
 * account through to the service and never invent one. These tests pin
 * that, and pin that a contact which is not yours produces a redirect
 * rather than a form full of somebody else's data.
 */
class ContactControllerTests {

    private ContactService contactService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        contactService = mock(ContactService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContactController(contactService)).build();
    }

    @Test
    void listShowsOnlyTheSessionOwnersContacts() throws Exception {
        Contact dana = new Contact("Dana", "Reyes", "dana.reyes@acme.example");
        when(contactService.findAllForOwner(7)).thenReturn(List.of(dana));

        mockMvc.perform(get("/contacts/list").sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/list-contacts"))
                .andExpect(model().attribute("contacts", List.of(dana)));

        verify(contactService).findAllForOwner(7);
    }

    @Test
    void editingSomeoneElsesContactRedirectsInsteadOfShowingIt() throws Exception {
        when(contactService.findByIdForOwner(42, 7)).thenReturn(Optional.empty());

        mockMvc.perform(get("/contacts/showFormForUpdate")
                        .param("contactId", "42")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contacts/list"));
    }

    @Test
    void editingYourOwnContactOpensTheForm() throws Exception {
        Contact mine = new Contact("Marcus", "Lee", "marcus.lee@initech.example");
        mine.setId(43);
        when(contactService.findByIdForOwner(43, 7)).thenReturn(Optional.of(mine));

        mockMvc.perform(get("/contacts/showFormForUpdate")
                        .param("contactId", "43")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts/contact-form"))
                .andExpect(model().attribute("contact", mine));
    }

    @Test
    void saveGoesThroughTheServiceWithTheSessionOwner() throws Exception {
        mockMvc.perform(post("/contacts/save")
                        .param("firstName", "Dana")
                        .param("lastName", "Reyes")
                        .param("email", "dana.reyes@acme.example")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(redirectedUrl("/contacts/list"));

        ArgumentCaptor<Contact> saved = ArgumentCaptor.forClass(Contact.class);
        verify(contactService).saveForOwner(saved.capture(), eq(7));
        assertEquals("dana.reyes@acme.example", saved.getValue().getEmail());
    }

    @Test
    void deleteGoesThroughTheServiceWithTheSessionOwner() throws Exception {
        mockMvc.perform(post("/contacts/delete")
                        .param("contactId", "42")
                        .sessionAttr(LoginController.SESSION_ACCOUNT_ID, 7))
                .andExpect(redirectedUrl("/contacts/list"));

        verify(contactService).deleteByIdForOwner(42, 7);
        verify(contactService, never()).saveForOwner(any(), anyInt());
    }
}
