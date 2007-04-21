//
// $Id$

package com.threerings.msoy.data;

import com.threerings.crowd.peer.data.CrowdClientInfo;

/**
 * Contains information on a player logged into one of our peer servers.
 */
public class MsoyClientInfo extends CrowdClientInfo
{
    /** The member's unique identifier. */
    public int memberId;
}
