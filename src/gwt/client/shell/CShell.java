//
// $Id$

package client.shell;

import java.util.MissingResourceException;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.IncompatibleRemoteServiceException;

import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.data.all.VisitorInfo;

import com.threerings.msoy.web.gwt.ServiceException;
import com.threerings.msoy.web.gwt.WebCreds;

import client.util.events.FlashEvents;
import client.util.events.GotGuestIdEvent;
import client.util.events.GotGuestIdListener;

/**
 * Contains a reference to the various bits that we're likely to need in the web client interface.
 */
public class CShell
{
    /** Our credentials or null if we are not logged in. */
    public static WebCreds creds;

    /** Used to communicate with the frame. */
    public static Frame frame;

    /** Current player's visitor info, valid for both logged in and guest players. */
    public static VisitorInfo visitor;

    /**
     * Returns our authentication token, or null if we don't have one.
     */
    public static String getAuthToken ()
    {
        return (creds == null) ? null : creds.token;
    }

    /**
     * Returns our member id if we're logged in, 0 if we are not.
     */
    public static int getMemberId ()
    {
        return (creds == null) ? 0 : creds.name.getMemberId();
    }

    /**
     * Returns true if we're an ephemeral guest, false if we're a permaguest or registered member.
     */
    public static boolean isGuest ()
    {
        return MemberName.isGuest(getMemberId());
    }

    /**
     * Returns true if we're a permaguest, false if we're an ephemeral guest or registered member.
     */
    public static boolean isPermaguest ()
    {
        return creds != null && (creds.role == WebCreds.Role.PERMAGUEST);
    }

    /**
     * Returns true if we are a registered user that may or may not have a validate email address,
     * false if we're a guest or permaguest.
     */
    public static boolean isRegistered ()
    {
        return creds != null && creds.isRegistered();
    }

    /**
     * Returns true if we are a registered user with a validated email address, false if we're a
     * guest, permaguest or a registered member with an unvalidated email address.
     */
    public static boolean isValidated ()
    {
        return creds != null && creds.isValidated();
    }

    /**
     * Returns true if we're logged in and have support+ privileges.
     */
    public static boolean isSupport ()
    {
        return (creds != null) && creds.isSupport();
    }

    /**
     * Returns true if we're logged in and have admin+ privileges.
     */
    public static boolean isAdmin ()
    {
        return (creds != null) && creds.isAdmin();
    }

    /**
     * Returns true if we're logged in and have maintainer privileges.
     */
    public static boolean isMaintainer ()
    {
        return (creds != null) && creds.isMaintainer();
    }

    /**
     * Initializes the shell and wires up some listeners.
     */
    public static void init (Frame frame)
    {
        CShell.frame = frame;

        FlashEvents.addListener(new GotGuestIdListener() {
            public void gotGuestId (GotGuestIdEvent event) {
                if (getMemberId() > 0) {
                    log("Warning: got guest id but appear to be logged in? " +
                        "[memberId=" + getMemberId() + ", guestId=" + event.getGuestId() + "].");
                } else {
                    log("Got guest id from Flash " + event.getGuestId() + ".");
                    creds = new WebCreds(
                        // TODO: the code that knows how to do this is in MsoyCredentials which is
                        // not accessible to GWT currently for unrelated technical reasons
                        "G" + event.getGuestId(), null,
                        new MemberName("Guest" + event.getGuestId(), event.getGuestId()), null,
                        WebCreds.Role.PERMAGUEST);
                }
            }
        });
    }

    /**
     * Looks up the appropriate response message for the supplied server-generated error.
     */
    public static String serverError (Throwable error)
    {
        if (error instanceof IncompatibleRemoteServiceException) {
            return _smsgs.xlate("version_mismatch");
        } else if (error instanceof ServiceException) {
            return serverError(error.getMessage());
        } else {
            return _smsgs.xlate("internal_error");
        }
    }

    /**
     * Looks up the appropriate response message for the supplied server-generated error.
     */
    public static String serverError (String error)
    {
        // ConstantsWithLookup can't handle things that don't look like method names, yay!
        if (error.startsWith("m.") || error.startsWith("e.")) {
            error = error.substring(2);
        }
        try {
            return _smsgs.xlate(error);
        } catch (MissingResourceException e) {
            // looking up a missing translation message throws an exception, yay!
            return "[" + error + "]";
        }
    }

    /** Reports a log message to the console. */
    public static void log (String message, Object... args)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(message);
        if (args.length > 1) {
            sb.append(" [");
            for (int ii = 0, ll = args.length/2; ii < ll; ii++) {
                if (ii > 0) {
                    sb.append(", ");
                }
                sb.append(args[2*ii]).append("=").append(args[2*ii+1]);
            }
            sb.append("]");
        }
        Object error = (args.length % 2 == 1) ? args[args.length-1] : null;
        if (GWT.isScript()) {
            if (error != null) {
                sb.append(": ").append(error);
            }
            consoleLog(sb.toString(), error);
        } else {
            GWT.log(sb.toString(), (Throwable)error);
        }
    }

    /**
     * Returns a partner identifier when we're running in partner cobrand mode, null when we're
     * running in the full Whirled environment.
     */
    public static native String getPartner () /*-{
        return $doc.whirledPartner;
    }-*/;

    /**
     * Records a log message to the JavaScript console.
     */
    protected static native void consoleLog (String message, Object error) /*-{
        if ($wnd.console) {
            if (error != null) {
                $wnd.console.info(message, error);
            } else {
                $wnd.console.info(message);
            }
        }
    }-*/;

    protected static final ServerLookup _smsgs = GWT.create(ServerLookup.class);
}
