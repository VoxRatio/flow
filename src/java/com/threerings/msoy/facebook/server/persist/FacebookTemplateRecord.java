//
// $Id$

package com.threerings.msoy.facebook.server.persist;

import com.samskivert.depot.Key;
import com.samskivert.depot.PersistentRecord;
import com.samskivert.depot.annotation.Entity;
import com.samskivert.depot.annotation.Id;
import com.samskivert.depot.expression.ColumnExp;
import com.threerings.msoy.facebook.gwt.FacebookTemplate;
import com.threerings.msoy.facebook.gwt.FacebookTemplateCard;

/**
 * Describes a story template entered into the Facebook template editor for use at runtime by the
 * msoy server.
 */
@Entity
public class FacebookTemplateRecord extends PersistentRecord
{
    // AUTO-GENERATED: FIELDS START
    public static final Class<FacebookTemplateRecord> _R = FacebookTemplateRecord.class;
    public static final ColumnExp APP_ID = colexp(_R, "appId");
    public static final ColumnExp CODE = colexp(_R, "code");
    public static final ColumnExp VARIANT = colexp(_R, "variant");
    public static final ColumnExp BUNDLE_ID = colexp(_R, "bundleId");
    // AUTO-GENERATED: FIELDS END

    /** Determines compatible schema versions. */
    public static final int SCHEMA_VERSION = 3;

    /** The id of the application defining this template. */
    @Id public int appId;

    /** Used by msoy to reference the functionality of this template. */
    @Id public String code;

    /** Distinguish between functionally equivalent templates. */
    @Id public String variant;

    /** Passed to Facebook when creating a new story. */
    public long bundleId;

    /**
     * Creates a new template to be filled in with data from the database.
     */
    public FacebookTemplateRecord ()
    {
    }

    /**
     * Creates a new template matching the given runtime template.
     */
    public FacebookTemplateRecord (int appId, FacebookTemplate template)
    {
        this.appId = appId;
        code = template.code;
        bundleId = template.bundleId;
        variant = template.variant;
    }

    /**
     * Creates and returns a runtime template matching this one.
     */
    public FacebookTemplate toTemplate ()
    {
        return new FacebookTemplate(code, variant, bundleId);
    }

    /**
     * Creates and returns a card for use on the non-admin client.
     */
    public FacebookTemplateCard toTemplateCard ()
    {
        FacebookTemplateCard card = new FacebookTemplateCard();
        card.bundleId = bundleId;
        card.variant = variant;
        return card;
    }

    /**
     * Get the entry vector associated with this template. This is so we can track the popularity
     * of variants.
     */
    public String toEntryVector ()
    {
        return FacebookTemplateCard.toEntryVector(code, variant);
    }

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link FacebookTemplateRecord}
     * with the supplied key values.
     */
    public static Key<FacebookTemplateRecord> getKey (int appId, String code, String variant)
    {
        return new Key<FacebookTemplateRecord>(
                FacebookTemplateRecord.class,
                new ColumnExp[] { APP_ID, CODE, VARIANT },
                new Comparable[] { appId, code, variant });
    }
    // AUTO-GENERATED: METHODS END
}
