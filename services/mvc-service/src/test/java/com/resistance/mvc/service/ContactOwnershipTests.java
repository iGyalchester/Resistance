package com.resistance.mvc.service;

import com.resistance.mvc.dao.ContactRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Contacts used to be a single shared directory: every signed-in user saw,
 * edited and deleted everyone's recruiters. They are now one address book
 * per account, and the boundary lives in ContactServiceImpl - another
 * account's contact must be indistinguishable from a missing one, and
 * never modifiable. Mirrors JobApplicationOwnershipTests.
 */
class ContactOwnershipTests {

    private ContactRepository contacts;
    private UserAccountRepository accounts;
    private AuditEventClient audit;
    private ContactServiceImpl service;

    private UserAccount me;
    private UserAccount someoneElse;
    private Contact theirs;

    @BeforeEach
    void setUp() {
        contacts = mock(ContactRepository.class);
        accounts = mock(UserAccountRepository.class);
        audit = mock(AuditEventClient.class);
        service = new ContactServiceImpl(contacts, accounts, audit);
        when(contacts.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        me = new UserAccount("Me", "me@example.com");
        me.setId(1);
        someoneElse = new UserAccount("Them", "them@example.com");
        someoneElse.setId(2);

        theirs = new Contact("Dana", "Reyes", "dana.reyes@acme.example", someoneElse);
        theirs.setId(42);
        when(contacts.findById(42)).thenReturn(Optional.of(theirs));
        when(accounts.findById(anyInt())).thenAnswer(inv ->
                (int) inv.getArgument(0) == 1 ? Optional.of(me) : Optional.of(someoneElse));
    }

    @Test
    void theListIsAskedForOneOwnersContactsOnly() {
        Contact mine = new Contact("Marcus", "Lee", "marcus.lee@initech.example", me);
        when(contacts.findByOwnerIdOrderByLastNameAsc(1)).thenReturn(List.of(mine));

        assertEquals(List.of(mine), service.findAllForOwner(1));
        verify(contacts, never()).findAll();
    }

    @Test
    void someoneElsesContactLooksMissing() {
        assertTrue(service.findByIdForOwner(42, 1).isEmpty());
        assertTrue(service.findByIdForOwner(42, 2).isPresent());
    }

    @Test
    void ownerlessContactIsInvisible() {
        theirs.setOwner(null);
        assertTrue(service.findByIdForOwner(42, 1).isEmpty());
    }

    @Test
    void deleteRefusesForeignContacts() {
        assertFalse(service.deleteByIdForOwner(42, 1));
        verify(contacts, never()).deleteById(anyInt());

        assertTrue(service.deleteByIdForOwner(42, 2));
        verify(contacts).deleteById(42);
    }

    @Test
    void updateOfForeignContactIsRejected() {
        Contact forged = new Contact("Dana", "Reyes", "attacker@evil.example");
        forged.setId(42); // trying to overwrite someone else's row

        assertThrows(IllegalArgumentException.class, () -> service.saveForOwner(forged, 1));
        verify(contacts, never()).save(any());
    }

    @Test
    void newContactIsStampedWithTheActingOwner() {
        Contact mine = new Contact("Marcus", "Lee", "marcus.lee@initech.example");

        service.saveForOwner(mine, 1);

        assertSame(me, mine.getOwner());
        verify(contacts).save(mine);
    }

    @Test
    void saveAndDeleteEmitAuditEvents() {
        Contact mine = new Contact("Marcus", "Lee", "marcus.lee@initech.example");
        service.saveForOwner(mine, 1);
        verify(audit).emit(eq("DATABASE_QUERY"), eq("CREATE"), eq("me@example.com"),
                startsWith("contact:"), isNull());

        theirs.setOwner(me);
        service.deleteByIdForOwner(42, 1);
        verify(audit).emit(eq("DATABASE_QUERY"), eq("DELETE"), eq("me@example.com"),
                eq("contact:42"), isNull());
    }

    @Test
    void refusedOperationsEmitNothing() {
        service.deleteByIdForOwner(42, 1); // theirs, not mine
        verifyNoInteractions(audit);
    }
}
