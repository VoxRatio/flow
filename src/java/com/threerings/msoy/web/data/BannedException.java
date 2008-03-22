//
// $Id$

package com.threerings.msoy.web.data;

/**
 * A special case ServiceExeption with extended ban related information.
 */
public class BannedException extends ServiceException
{
    /**
     * Creates a banned exception with the supplied translation message, warning and time until
     * ban expires in hours or -1 for permanent ban.
     */
    public BannedException (String message, String warning, int expires)
    {
        super(message);
        _warning = warning;
        _expires = expires;
    }

    /**
     * Default constructor for use when unserializing.
     */
    public BannedException ()
    {
    }

    public String getWarning ()
    {
        return _warning;
    }

    public int getExpires ()
    {
        return _expires;
    }

    protected String _warning;
    protected int _expires;
}
