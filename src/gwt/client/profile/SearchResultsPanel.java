//
// $Id$

package client.profile;

import java.util.List;

import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.VerticalPanel;

import com.threerings.gwt.ui.PagedGrid;
import com.threerings.gwt.util.SimpleDataModel;

import client.util.ProfileGrid;

public class SearchResultsPanel extends VerticalPanel
{
    /** The number of columns of items to display. */
    public static final int COLUMNS = 2;

    /** The number of rows of items to display. */
    public static final int ROWS = 3;

    public SearchResultsPanel (List profiles, int page)
    {
        setStyleName("searchResultsPanel");
        setWidth("100%");

        add(_profiles = new ProfileGrid(ROWS, COLUMNS));
        _profiles.setModel(new SimpleDataModel(profiles), page);
    }

    public void displayPage (int page) 
    {
        _profiles.displayPage(page, false);
    }

    protected ProfileGrid _profiles;
}
