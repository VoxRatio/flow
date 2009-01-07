//
// $Id$

package com.threerings.msoy.peer.server;

import com.threerings.io.Streamable;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.peer.data.MsoyPeerMarshaller;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationDispatcher;
import com.threerings.presents.server.InvocationException;

/**
 * Dispatches requests to the {@link MsoyPeerProvider}.
 */
public class MsoyPeerDispatcher extends InvocationDispatcher<MsoyPeerMarshaller>
{
    /**
     * Creates a dispatcher that may be registered to dispatch invocation
     * service requests for the specified provider.
     */
    public MsoyPeerDispatcher (MsoyPeerProvider provider)
    {
        this.provider = provider;
    }

    @Override // documentation inherited
    public MsoyPeerMarshaller createMarshaller ()
    {
        return new MsoyPeerMarshaller();
    }

    @Override // documentation inherited
    public void dispatchRequest (
        ClientObject source, int methodId, Object[] args)
        throws InvocationException
    {
        switch (methodId) {
        case MsoyPeerMarshaller.FORWARD_MEMBER_OBJECT:
            ((MsoyPeerProvider)provider).forwardMemberObject(
                source, (MemberObject)args[0], (Streamable[])args[1]
            );
            return;

        case MsoyPeerMarshaller.RECLAIM_ITEM:
            ((MsoyPeerProvider)provider).reclaimItem(
                source, ((Integer)args[0]).intValue(), ((Integer)args[1]).intValue(), (ItemIdent)args[2], (InvocationService.ConfirmListener)args[3]
            );
            return;

        default:
            super.dispatchRequest(source, methodId, args);
            return;
        }
    }
}
