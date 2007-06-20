//
// $Id$

package com.threerings.msoy.web.data;

import java.util.List;

import com.google.gwt.user.client.rpc.IsSerializable;

public class MemberInviteResult
    implements IsSerializable
{
    /** The member's memberId */
    public int memberId = 0;

    /** The member's permaname, or if none his display name. */
    public String name = "";

    /** The list of the people that this member has invited. */
    public List invitees;
}
