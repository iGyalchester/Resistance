package com.resistance.mvc.service;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The multi-user boundary lives in JobApplicationServiceImpl: another
 * account's application must be indistinguishable from a missing one,
 * and never modifiable.
 */
class JobApplicationOwnershipTests {

    private JobApplicationRepository applications;
    private UserAccountRepository accounts;
    private JobApplicationServiceImpl service;

    private UserAccount me;
    private UserAccount someoneElse;
    private JobApplication theirs;

    @BeforeEach
    void setUp() {
        applications = mock(JobApplicationRepository.class);
        accounts = mock(UserAccountRepository.class);
        service = new JobApplicationServiceImpl(applications, accounts);

        me = new UserAccount("Me", "me@example.com");
        me.setId(1);
        someoneElse = new UserAccount("Them", "them@example.com");
        someoneElse.setId(2);

        theirs = new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.APPLIED);
        theirs.setId(42);
        theirs.setOwner(someoneElse);
        when(applications.findById(42)).thenReturn(Optional.of(theirs));
        when(accounts.findById(anyInt())).thenAnswer(inv ->
                (int) inv.getArgument(0) == 1 ? Optional.of(me) : Optional.of(someoneElse));
    }

    @Test
    void someoneElsesApplicationLooksMissing() {
        assertTrue(service.findByIdForOwner(42, 1).isEmpty());
        assertTrue(service.findByIdForOwner(42, 2).isPresent());
    }

    @Test
    void ownerlessApplicationIsInvisible() {
        theirs.setOwner(null);
        assertTrue(service.findByIdForOwner(42, 1).isEmpty());
    }

    @Test
    void deleteRefusesForeignApplications() {
        assertFalse(service.deleteByIdForOwner(42, 1));
        verify(applications, never()).deleteById(anyInt());

        assertTrue(service.deleteByIdForOwner(42, 2));
        verify(applications).deleteById(42);
    }

    @Test
    void updateOfForeignApplicationIsRejected() {
        JobApplication forged = new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.OFFER);
        forged.setId(42); // trying to overwrite someone else's row

        assertThrows(IllegalArgumentException.class, () -> service.saveForOwner(forged, 1));
        verify(applications, never()).save(any());
    }

    @Test
    void newApplicationIsStampedWithTheActingOwner() {
        JobApplication mine = new JobApplication("Globex", "Data Engineer", ApplicationStatus.APPLIED);

        service.saveForOwner(mine, 1);

        assertSame(me, mine.getOwner());
        verify(applications).save(mine);
    }
}
