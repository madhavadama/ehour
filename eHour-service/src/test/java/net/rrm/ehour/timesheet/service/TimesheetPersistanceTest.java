/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package net.rrm.ehour.timesheet.service;

import net.rrm.ehour.data.DateRange;
import net.rrm.ehour.domain.*;
import net.rrm.ehour.exception.OverBudgetException;
import net.rrm.ehour.mail.service.ProjectManagerNotifierService;
import net.rrm.ehour.persistence.timesheet.dao.TimesheetCommentDao;
import net.rrm.ehour.persistence.timesheet.dao.TimesheetDao;
import net.rrm.ehour.project.status.ProjectAssignmentStatus;
import net.rrm.ehour.project.status.ProjectAssignmentStatus.Status;
import net.rrm.ehour.project.status.ProjectAssignmentStatusService;
import net.rrm.ehour.report.reports.element.AssignmentAggregateReportElement;
import net.rrm.ehour.util.EhourConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TimesheetPersistanceTest {
    private TimesheetPersistance persister;

    @Mock
    private TimesheetDao timesheetDAO;

    @Mock
    private ProjectManagerNotifierService projectManagerNotifierService;

    @Mock
    private ProjectAssignmentStatusService statusService;

    private ProjectAssignment assignment;
    private List<TimesheetEntry> newEntries;
    private List<TimesheetEntry> existingEntries;

    @Mock
    private TimesheetCommentDao commentDao;

    @Mock
    private ApplicationContext context;

    @Before
    public void setUp() {
        persister = new TimesheetPersistance(timesheetDAO, commentDao, statusService, projectManagerNotifierService, context);

        initData();
    }

    @SuppressWarnings("deprecation") //new dates
    private void initData() {
        assignment = ProjectAssignmentObjectMother.createProjectAssignment(1);
        assignment.getProject().setProjectManager(UserObjectMother.createUser());
        assignment.setNotifyPm(true);

        assignment.setAssignmentType(new ProjectAssignmentType(EhourConstants.ASSIGNMENT_TIME_ALLOTTED_FLEX));

        newEntries = new ArrayList<TimesheetEntry>();

        Date dateA = new Date(2008 - 1900, 4 - 1, 1);
        Date dateB = new Date(2008 - 1900, 4 - 1, 2);

        {
            TimesheetEntry entry = new TimesheetEntry();
            TimesheetEntryId id = new TimesheetEntryId();
            id.setProjectAssignment(assignment);
            id.setEntryDate(dateA);
            entry.setEntryId(id);
            entry.setHours(8f);
            newEntries.add(entry);
        }

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setProjectAssignment(assignment);
            idDel.setEntryDate(dateB);
            entryDel.setEntryId(idDel);
            entryDel.setHours(null);
            newEntries.add(entryDel);
        }

        existingEntries = new ArrayList<TimesheetEntry>();
        {
            TimesheetEntry entry = new TimesheetEntry();
            TimesheetEntryId id = new TimesheetEntryId();
            id.setProjectAssignment(assignment);
            id.setEntryDate(dateA);
            entry.setEntryId(id);
            entry.setHours(5f);
            existingEntries.add(entry);
        }

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setProjectAssignment(assignment);
            idDel.setEntryDate(dateB);
            entryDel.setEntryId(idDel);
            entryDel.setHours(5f);
            existingEntries.add(entryDel);
        }
    }

    @Test
    public void should_persist_new_timesheet() throws OverBudgetException {
        DateRange dateRange = new DateRange();

        when(statusService.getAssignmentStatus(assignment)).thenReturn(new ProjectAssignmentStatus());

        persister.validateAndPersist(assignment, newEntries, dateRange);

        verify(statusService, times(2)).getAssignmentStatus(assignment);
        verify(timesheetDAO).persist(any(TimesheetEntry.class));
        verify(timesheetDAO).getTimesheetEntriesInRange(assignment, dateRange);
    }

    @Test
    public void should_update_existing_timesheet() throws OverBudgetException {
        DateRange dateRange = new DateRange();

        when(timesheetDAO.getTimesheetEntriesInRange(any(ProjectAssignment.class), eq(dateRange))).thenReturn(existingEntries);
        when(statusService.getAssignmentStatus(assignment)).thenReturn(new ProjectAssignmentStatus());

        persister.validateAndPersist(assignment, newEntries, dateRange);

        verify(statusService, times(2)).getAssignmentStatus(assignment);
        verify(timesheetDAO).delete(any(TimesheetEntry.class));
        verify(timesheetDAO).merge(any(TimesheetEntry.class));
    }


    @Test
    public void should_not_persist_an_timesheet_that_went_overbudget() {
        DateRange dateRange = new DateRange();

        when(timesheetDAO.getTimesheetEntriesInRange(any(ProjectAssignment.class), eq(dateRange))).thenReturn(existingEntries);

        // before persist
        ProjectAssignmentStatus validStatus = new ProjectAssignmentStatus();
        when(statusService.getAssignmentStatus(assignment)).thenReturn(validStatus);

        // after persist
        ProjectAssignmentStatus invalidStatus = new ProjectAssignmentStatus();
        invalidStatus.addStatus(Status.OVER_OVERRUN);
        invalidStatus.setValid(false);

        when(statusService.getAssignmentStatus(assignment)).thenReturn(validStatus, invalidStatus);

        try {
            persister.validateAndPersist(assignment, newEntries, dateRange);
            fail();
        } catch (OverBudgetException e) {
            verify(timesheetDAO).merge(any(TimesheetEntry.class));
            verify(timesheetDAO).delete(any(TimesheetEntry.class));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void should_allow_to_decrease_existing_hours_even_when_project_is_over_budget() throws OverBudgetException {
        Date dateC = new Date(2008 - 1900, Calendar.APRIL, 3);

        newEntries.clear();
        existingEntries.clear();

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setProjectAssignment(assignment);
            idDel.setEntryDate(dateC);
            entryDel.setEntryId(idDel);
            entryDel.setHours(7f);
            newEntries.add(entryDel);
        }

        {
            TimesheetEntry entryDel = new TimesheetEntry();
            TimesheetEntryId idDel = new TimesheetEntryId();
            idDel.setProjectAssignment(assignment);
            idDel.setEntryDate(dateC);
            entryDel.setEntryId(idDel);
            entryDel.setHours(8f);
            existingEntries.add(entryDel);
        }

        when(timesheetDAO.getTimesheetEntriesInRange(any(ProjectAssignment.class), any(DateRange.class))).thenReturn(existingEntries);

        ProjectAssignmentStatus beforeStatus = new ProjectAssignmentStatus();
        beforeStatus.addStatus(Status.OVER_OVERRUN);
        beforeStatus.setValid(false);

        ProjectAssignmentStatus afterStatus = new ProjectAssignmentStatus();
        afterStatus.addStatus(Status.OVER_OVERRUN);
        afterStatus.setValid(false);

        when(statusService.getAssignmentStatus(assignment)).thenReturn(beforeStatus, afterStatus);

        persister.validateAndPersist(assignment, newEntries, new DateRange());

        verify(timesheetDAO).merge(any(TimesheetEntry.class));
    }

    @Test
    public void should_not_allow_to_book_more_hours_when_the_project_is_overbudget() {
        when(timesheetDAO.getTimesheetEntriesInRange(any(ProjectAssignment.class), any(DateRange.class))).thenReturn(existingEntries);

        ProjectAssignmentStatus beforeStatus = new ProjectAssignmentStatus();
        beforeStatus.setValid(false);

        ProjectAssignmentStatus afterStatus = new ProjectAssignmentStatus();
        afterStatus.addStatus(Status.OVER_OVERRUN);
        afterStatus.setValid(false);

        when(statusService.getAssignmentStatus(assignment)).thenReturn(beforeStatus, afterStatus);

        try {
            persister.validateAndPersist(assignment, newEntries, new DateRange());
            fail();
        } catch (OverBudgetException ignored) {

        }
    }

    @Test
    public void should_mail_pm_when_status_of_project_changes() throws OverBudgetException {
        when(timesheetDAO.getLatestTimesheetEntryForAssignment(assignment.getAssignmentId())).thenReturn(newEntries.get(0));
        when(timesheetDAO.getTimesheetEntriesInRange(any(ProjectAssignment.class), any(DateRange.class))).thenReturn(existingEntries);

        ProjectAssignmentStatus beforeStatus = new ProjectAssignmentStatus();
        beforeStatus.addStatus(Status.IN_ALLOTTED);
        beforeStatus.setValid(true);

        ProjectAssignmentStatus afterStatus = new ProjectAssignmentStatus();
        afterStatus.addStatus(Status.IN_OVERRUN);
        afterStatus.setValid(true);
        afterStatus.setAggregate(new AssignmentAggregateReportElement());

        when(statusService.getAssignmentStatus(assignment)).thenReturn(beforeStatus);

        when(statusService.getAssignmentStatus(assignment)).thenReturn(beforeStatus, afterStatus);

        persister.validateAndPersist(assignment, newEntries, new DateRange());

        verify(timesheetDAO).delete(any(TimesheetEntry.class));
        verify(timesheetDAO).merge(any(TimesheetEntry.class));
        verify(projectManagerNotifierService).mailPMFlexAllottedReached(any(AssignmentAggregateReportElement.class), any(Date.class), eq(assignment.getProject().getProjectManager()));
    }

    @Test
    public void should_persist_individual_timesheet_entries_for_a_week() {
        TimesheetCommentId commentId = new TimesheetCommentId(1, new Date());
        TimesheetComment comment = new TimesheetComment(commentId, "comment");

        when(context.getBean(IPersistTimesheet.class)).thenReturn(persister); // through Spring for new TX per entr=y

        when(statusService.getAssignmentStatus(assignment)).thenReturn(new ProjectAssignmentStatus());

        persister.persistTimesheetWeek(newEntries, comment, new DateRange());

        verify(commentDao).persist(comment);
    }
}
