//
// $Id$

package com.threerings.msoy.item.data.all;

/**
 * Contains the runtime data for a TrophySource item.
 */
public class TrophySource extends SubItem
{
    /** The required width for a trophy image. */
    public static final int TROPHY_WIDTH = 60;

    /** The required height for a trophy image. */
    public static final int TROPHY_HEIGHT = 60;

    /** The order in which to display this trophy compared to other trophies. */
    public int sortOrder;

    /** Whether or not this trophy's description is a secret. */
    public boolean secret;

    @Override // from Item
    public byte getType ()
    {
        return TROPHY_SOURCE;
    }

    @Override // from Item
    public byte getSuiteMasterType ()
    {
        return GAME;
    }

    @Override // from Item
    public MediaDesc getPreviewMedia ()
    {
        return thumbMedia;
    }

    @Override // from Item
    public MediaDesc getPrimaryMedia ()
    {
        return thumbMedia;
    }

    @Override // from Item
    public boolean isConsistent ()
    {
        return super.isConsistent() && nonBlank(name, MAX_NAME_LENGTH) && (thumbMedia != null);
    }

    @Override // from SubItem
    public boolean isSalable ()
    {
        return false;
    }
}
