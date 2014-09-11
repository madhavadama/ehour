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

package net.rrm.ehour.timesheet.dto;

import net.rrm.ehour.report.reports.element.ActivityAggregateReportElement;

/**
 * Value object for timesheet overview. While the hours in an aggregate reflect
 * only a certain period, totalBookedHours is all hours booked on this
 * assignment
 */

public class UserProjectStatus extends ActivityAggregateReportElement {
    private static final long serialVersionUID = 2321889010790629630L;
    private Number totalBookedHours;

    public UserProjectStatus() {
    }
    
    public UserProjectStatus(ActivityAggregateReportElement aggregate) {
        this(aggregate, null);
    }

    public UserProjectStatus(ActivityAggregateReportElement aggregate, Number totalBookedHours) {
        super(aggregate.getActivity(), aggregate.getHours());
        this.totalBookedHours = totalBookedHours;
    }

	/**
	 * Get fixed hours remaining to book on this project This is applicable to
	 * fixed and flex assignments
	 * 
	 * @return
	 */
    public Float getFixedHoursRemaining()
    {
        Float	remainder = null;

        if (totalBookedHours != null) {
            remainder = getActivity().getAllottedHours() - totalBookedHours.floatValue();
        }

        return remainder;
    }

	/**
	 * @return the totalBookedHours
	 */
	public Number getTotalBookedHours() {
		return totalBookedHours;
	}

	/**
	 * @param totalBookedHours
	 *            the totalBookedHours to set
	 */
	public void setTotalBookedHours(Number totalBookedHours) {
		this.totalBookedHours = totalBookedHours;
	}

}
