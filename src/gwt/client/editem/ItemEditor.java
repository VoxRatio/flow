//
// $Id$

package client.editem;

import java.util.HashMap;
import java.util.Map;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.stuff.gwt.StuffService;
import com.threerings.msoy.stuff.gwt.StuffServiceAsync;

import client.shell.CShell;
import client.shell.DynamicLookup;
import client.shell.ShellMessages;
import client.ui.LimitedTextArea;
import client.ui.MsoyUI;
import client.ui.StyledTabPanel;
import client.util.ServiceUtil;
import client.util.MsoyCallback;

/**
 * The base class for an interface for creating and editing digital items.
 */
public abstract class ItemEditor extends FlowPanel
{
    public static class Binder
    {
        public void textUpdated (String newText) {
        }
        public void valueChanged () {
        }
    }

    public static interface MediaUpdater
    {
        /**
         * Return null, or a message indicating why the specified media will not do.
         *
         * @param name the name of the uploaded file (from the user's local filesystem).
         * @param desc a media descriptor referencing the uploaded media.
         * @param width if the media is a non-thumbnail image this will contain the width of the
         * image, otherwise zero.
         * @param height if the media is a non-thumbnail image this will contain the height of the
         * image, otherwise zero.
         */
        String updateMedia (String name, MediaDesc desc, int width, int height);
    }

    /**
     * Creates an item editor interface for items of the specified type.  Returns null if the type
     * is unknown.
     */
    public static ItemEditor createItemEditor (byte type, EditorHost host)
    {
        ItemEditor editor = null;
        if (type == Item.PHOTO) {
            editor = new PhotoEditor();
        } else if (type == Item.DOCUMENT) {
            editor = new DocumentEditor();
        } else if (type == Item.FURNITURE) {
            editor = new FurnitureEditor();
        } else if (type == Item.GAME) {
            editor = new GameEditor();
        } else if (type == Item.AVATAR) {
            editor = new AvatarEditor();
        } else if (type == Item.PET) {
            editor = new PetEditor();
        } else if (type == Item.AUDIO) {
            editor = new AudioEditor();
        } else if (type == Item.VIDEO) {
            editor = new VideoEditor();
        } else if (type == Item.DECOR) {
            editor = new DecorEditor();
        } else if (type == Item.TOY) {
            editor = new ToyEditor();
        } else if (type == Item.LEVEL_PACK) {
            editor = new LevelPackEditor();
        } else if (type == Item.ITEM_PACK) {
            editor = new ItemPackEditor();
        } else if (type == Item.TROPHY_SOURCE) {
            editor = new TrophySourceEditor();
        } else if (type == Item.PRIZE) {
            editor = new PrizeEditor();
        } else if (type == Item.PROP) {
            editor = new PropEditor();
        } else {
            return null; // woe be the caller
        }
        editor.init(type, host);
        return editor;
    }

    public ItemEditor ()
    {
        // we have to do this wacky singleton crap because GWT and/or JavaScript doesn't seem to
        // cope with our trying to create an anonymous function that calls an instance method on a
        // JavaScript object
        _singleton = this;

        setStyleName("itemEditor");
        add(MsoyUI.createImage("/images/item/editor_box_top.png", "RoundedTop"));
        add(_header = new AbsolutePanel());
        add(_content = new FlexTable());
        _content.setStyleName("Content");
        _currentTab = _content;
        add(MsoyUI.createImage("/images/item/editor_box_bottom.png", "RoundedBottom"));

        addInfo();
        addExtras();
        addDescription();

        _esubmit = new Button("submit");
        _esubmit.addClickListener(new ClickListener() {
            public void onClick (Widget widget) {
                commitEdit();
            }
        });
        Button ecancel = new Button(_cmsgs.cancel());
        ecancel.addClickListener(new ClickListener() {
            public void onClick (Widget widget) {
                _parent.editComplete(null);
            }
        });

        addSpacer();
        int row = _content.getRowCount();
        _content.setWidget(row, 1, MsoyUI.createButtonPair(_esubmit, ecancel));
        _content.getFlexCellFormatter().setHorizontalAlignment(row, 1, HasAlignment.ALIGN_RIGHT);
    }

    /**
     * Configures this editor with a reference to the item service and its item panel parent.
     */
    public void init (byte type, EditorHost parent)
    {
        _type = type;
        _parent = parent;
    }

    /**
     * Configures this editor with an item to edit. The item may be freshly constructed if we are
     * using the editor to create a new item.
     */
    public void setItem (Item item)
    {
        _item = item;
        _esubmit.setText(_emsgs.editorSave());

        safeSetText(_name, _item.name);
        if (_description != null && _item.description != null) {
            _description.setText(_item.description);
        }

        // build the header now that we know type and whether we are creating or editing
        if (_header.getWidgetCount() == 0) {
            _header.setStyleName("Header");
            String title = _item.itemId == 0
                ? _emsgs.createTitle(_dmsgs.xlate("itemType" + _type))
                : _emsgs.editTitle(_dmsgs.xlate("itemType" + _type));
            _header.add(MsoyUI.createLabel(title, "Title"), 15, 0);
            _header.add(MsoyUI.createHTML(_dmsgs.xlate("editorWikiLink" + _type), "WikiLink"),
                250, 5);
            _header.add(MsoyUI.createImage("/images/item/editor_divider.png", null), 20, 30);
            _header.add(MsoyUI.createHTML(_dmsgs.xlate("editorBlurb" + _type), "Blurb"), 15, 40);
        }

        recheckFurniMedia();
        recheckThumbMedia();
    }

    /**
     * Configures the parent item to use when creating an item that is part of an item suite.
     */
    public void setParentItem (ItemIdent parentItem)
    {
        _parentItem = parentItem;
    }

    /**
     * Returns the currently configured item.
     */
    public Item getItem ()
    {
        return _item;
    }

    /**
     * Creates a blank item for use when creating a new item using this editor.
     */
    public abstract Item createBlankItem ();

    @Override // from Widget
    protected void onLoad ()
    {
        super.onLoad();
        configureBridge();
    }

    /**
     * Adds the non-media metadata editing fields for this item. By default this is only its name.
     */
    protected void addInfo ()
    {
        addRow(_emsgs.editorName(), bind(_name = new TextBox(), new Binder() {
            @Override public void textUpdated (String text) {
                _item.name = text;
            }
        }));
        _name.setMaxLength(Item.MAX_NAME_LENGTH);
    }

    /**
     * Adds the item description to the editor interface. Items that do not require a description
     * can override this method with a null body.
     */
    protected void addDescription ()
    {
        addSpacer();
        _description = new LimitedTextArea(Item.MAX_DESCRIPTION_LENGTH, 40, 3);
        bind(_description.getTextArea(), new Binder() {
            @Override public void textUpdated (String text) {
                _item.description = text;
            }
        });
        addRow(_emsgs.editorDescrip(), _description, _emsgs.editorDescripTip());
    }

    /**
     * Derived classes can add additional editable components to the display by overriding this
     * method. Anything added before a call to super will go above the furniture and thumbnail
     * image uploaders, anything added after will go after.
     */
    protected void addExtras ()
    {
        addFurniUploader();
        addThumbUploader();
        _uploaders.get(Item.THUMB_MEDIA).setHint(getThumbnailHint());
    }

    /**
     * Returns the hint displayed next to the thumbnail uploader.
     */
    protected String getThumbnailHint ()
    {
        return _emsgs.editorThumbHint(
            String.valueOf(MediaDesc.THUMBNAIL_WIDTH), String.valueOf(MediaDesc.THUMBNAIL_HEIGHT));
    }

    protected void addFurniUploader ()
    {
        addSpacer();
        addRow(getFurniTabText(), createFurniUploader(true, new MediaUpdater() {
            public String updateMedia (String name, MediaDesc desc, int width, int height) {
                if (!isValidPrimaryMedia(desc)) {
                    return invalidPrimaryMediaMessage();
                }
                _item.setFurniMedia(desc);
                return null;
            }
        }), getFurniTitleText());
    }

    protected String getFurniTabText ()
    {
        return _emsgs.editorFurniTab();
    }

    protected String getFurniTitleText ()
    {
        return _emsgs.editorFurniTitle();
    }

    protected void addThumbUploader ()
    {
        addSpacer();
        addRow(_emsgs.editorThumbTab(), createThumbUploader(new MediaUpdater() {
            public String updateMedia (String name, MediaDesc desc, int width, int height) {
                if (!desc.isImage()) {
                    return _emsgs.errThumbNotImage();
                }
                _item.setThumbnailMedia(desc);
                return null;
            }
        }), _emsgs.editorThumbTitle());
    }

    /**
     * Is the specified MediaDesc a valid primary media for item type?
     */
    protected boolean isValidPrimaryMedia (MediaDesc desc)
    {
        if (_type == Item.AVATAR || _type == Item.PET || _type == Item.TOY) {
            // these must be swfs or remixable
            return desc.isSWF() || desc.isRemixable();

        } else if (_type == Item.FURNITURE || _type == Item.DECOR) {
            // these can be swfs, images, or remixable
            return desc.hasFlashVisual() || desc.isRemixable();

        } else {
            // other types are not yet remixable
            return desc.hasFlashVisual();
        }
    }

    /**
     * String returned if the primary media is invalid. Subclasses should override as appropriate.
     */
    protected String invalidPrimaryMediaMessage ()
    {
        return _emsgs.errFurniNotFlash();
    }

    protected void safeSetText (TextBoxBase box, String value)
    {
        if (box != null && value != null) {
            box.setText(value);
        }
    }

    /**
     * Creates a tab and activates it such that all subsequent configuration values (added with
     * methods like {@link #addRow} and {@link #addTip} are added to the most recently added tab's
     * internal table rather than to the top-level item editor.
     */
    protected void addTab (String label)
    {
        if (_tabs == null) {
            int row = _content.getRowCount();
            _content.setWidget(row, 0, _tabs = new StyledTabPanel());
            _tabs.setWidth("100%");
            _content.getFlexCellFormatter().setColSpan(row, 0, 2);
        }
        _tabs.add(_currentTab = new FlexTable(), label);
        _tabs.selectTab(0);
    }

    /**
     * Switches to the tab with the specified index. New components added via {@link #addRow},
     * etc. will go onto the specified tab. This of course requires that {@link #addTab} has been
     * called to add the tab in question.
     */
    protected void switchToTab (int tabIdx)
    {
        _currentTab = (FlexTable)_tabs.getWidget(tabIdx);
    }

    /**
     * Helper function for overriders of {@link #addInfo} etc.
     */
    protected void addRow (String label, Widget widget)
    {
        addRow(label, widget, null);
    }

    /**
     * Helper function for overriders of {@link #addInfo} etc.
     */
    protected void addRow (String label, Widget widget, String tip)
    {
        int row = _currentTab.getRowCount();
        // this aims to make the label column skinny; it even works on some browsers...
        _currentTab.getFlexCellFormatter().setWidth(row, 0, "150px");
        if (tip != null) {
            FlowPanel flow = new FlowPanel();
            flow.add(MsoyUI.createLabel(label, "nowrapLabel"));
            flow.add(MsoyUI.createLabel(tip, "tipLabel"));
            _currentTab.setWidget(row, 0, flow);
        } else {
            _currentTab.setText(row, 0, label);
            _currentTab.getFlexCellFormatter().setStyleName(row, 0, "nowrapLabel");
        }
        _currentTab.getFlexCellFormatter().setVerticalAlignment(row, 0, HasAlignment.ALIGN_TOP);
        _currentTab.setWidget(row, 1, widget);
    }

    /**
     * Helper function for overriders of {@link #addInfo} etc.
     */
    protected void addRow (Widget widget)
    {
        int row = _currentTab.getRowCount();
        _currentTab.setWidget(row, 0, widget);
        _currentTab.getFlexCellFormatter().setColSpan(row, 0, 2);
        _currentTab.getFlexCellFormatter().setStyleName(row, 0, "Item");
    }

    /**
     * Helper function for overriders of {@link #addInfo} etc.
     */
    protected void addTip (String tip)
    {
        int row = _currentTab.getRowCount();
        _currentTab.setText(row, 1, tip);
        _currentTab.getFlexCellFormatter().setStyleName(row, 1, "tipLabel");
        _currentTab.getFlexCellFormatter().setWidth(row, 1, "400px");
    }

    /**
     * Helper function for overriders of {@link #addInfo} etc.
     */
    protected void addSpacer ()
    {
        int row = _currentTab.getRowCount();
        _currentTab.setText(row, 0, " ");
        _currentTab.getFlexCellFormatter().setStyleName(row, 0, "tipLabel");
        _currentTab.getFlexCellFormatter().setHeight(row, 0, "10px");
        _currentTab.getFlexCellFormatter().setColSpan(row, 0, 2);
    }

    /**
     * This should be called by item editors that are used for editing media that has a 'main'
     * piece of media.
     * @param isPhoto if True, a thubmnail and 320x200 furni will also be generated and returned
     */
    protected ItemMediaUploader createMainUploader (String type, boolean isPhoto,
        MediaUpdater updater)
    {
        String mediaIds = Item.MAIN_MEDIA;
        mediaIds += isPhoto ? ";" + Item.THUMB_MEDIA + ";" + Item.FURNI_MEDIA : "";
        return createUploader(mediaIds, type, ItemMediaUploader.MODE_NORMAL, updater);
    }

    /**
     * This should be called by item editors that are used for editing media that has an additional
     * piece of media in addition to main, furni and thumbnail.
     */
    protected ItemMediaUploader createAuxUploader (String type, MediaUpdater updater)
    {
        return createUploader(Item.AUX_MEDIA, type, ItemMediaUploader.MODE_NORMAL, updater);
    }

    /**
     * This should be called if item editors want to create a custom furni uploader.
     * @param genThumb if True, a thubmnail will also be generated and returned for images
     */
    protected ItemMediaUploader createFurniUploader (boolean genThumb, MediaUpdater updater)
    {
        String mediaIds = Item.FURNI_MEDIA;
        mediaIds += genThumb ? ";" + Item.THUMB_MEDIA : "";
        return createUploader(mediaIds, TYPE_FLASH, ItemMediaUploader.MODE_NORMAL, updater);
    }

    /**
     * This should be called if item editors want to create a custom thumbnail uploader.
     */
    protected ItemMediaUploader createThumbUploader (MediaUpdater updater)
    {
        return createUploader(Item.THUMB_MEDIA, TYPE_IMAGE, ItemMediaUploader.MODE_THUMB, updater);
    }

    /**
     * Creates and configures a media uploader.
     */
    protected ItemMediaUploader createUploader (
        String mediaIds, String type, int mode, MediaUpdater updater)
    {
        ItemMediaUploader uploader = createUploaderWidget(mediaIds, type, mode, updater);
        // record the uploader under the MAIN mediaId.
        _uploaders.put(mediaIds.split(";")[0], uploader);
        return uploader;
    }

    /**
     * Just creates the uploader.
     */
    protected ItemMediaUploader createUploaderWidget (
        String mediaIds, String type, int mode, MediaUpdater updater)
    {
        return new ItemMediaUploader(mediaIds, type, mode, updater);
    }

    /**
     * Updates the media displayed by the specified uploader if it exists.
     */
    protected void setUploaderMedia (String id, MediaDesc desc)
    {
        ItemMediaUploader uploader = _uploaders.get(id);
        if (uploader != null) {
            uploader.setMedia(desc);
        }
    }

    /**
     * If an item wishes to use the filename of its primary as a default name for the item if one
     * is not already set, it should call this method with the filename when its primary media is
     * uploaded. This method will massage the supplied name (stripping its extension) and configure
     * it as the item name iff the item has no name already configured.
     */
    protected void maybeSetNameFromFilename (String name)
    {
        if (_name.getText().length() != 0) {
            return;
        }

        // if the name has a path, strip it
        int idx = name.lastIndexOf("\\"); // windows
        if (idx != -1) {
            name = name.substring(idx+1);
        }
        idx = name.lastIndexOf("/"); // joonix (and Mac OS X by association)
        if (idx != -1) {
            name = name.substring(idx+1);
        }

        // if the name has a file suffix, strip it
        idx = name.lastIndexOf(".");
        switch (name.length() - idx) {
        case 3:
        case 4:
            name = name.substring(0, idx);
            break;
        }

        // replace _ with space
        name = name.replaceAll("_", " ");

        _name.setText(name); // this doesn't trigger a text edited event
        _item.name = name; // so we have to do this by hand
    }

    /**
     * Configures this item editor with the hash value for media that it is about to upload.
     */
    protected void setHash (String id, String mediaHash, int mimeType, int constraint,
                            int width, int height)
    {
        ItemMediaUploader mu = _uploaders.get(id);
        if (mu == null) {
            CShell.log("Got setHash() request for unknown uploader [id=" + id + "].");
            return;
        }

        // set the new media in preview and in the item
        mu.setUploadedMedia(
            new MediaDesc(mediaHash, (byte)mimeType, (byte)constraint), width, height);

        // have the item re-validate that no media ids are duplicated unnecessarily
        _item.checkConsolidateMedia();

        // re-check the furni image as it may have changed
        if (!Item.FURNI_MEDIA.equals(id)) {
            recheckFurniMedia();
        }
    }

    /**
     * Called to re-set the displayed furni media to the MediaDesc returned by the item.
     */
    protected void recheckFurniMedia ()
    {
        setUploaderMedia(Item.FURNI_MEDIA, _item.getFurniMedia());
    }

    /**
     * Called to re-set the displayed thumb media to the MediaDesc returned by the item.
     */
    protected void recheckThumbMedia ()
    {
        setUploaderMedia(Item.THUMB_MEDIA, _item.getThumbnailMedia());
    }

    /**
     * This is called from our magical JavaScript method by JavaScript code received from the
     * server as a response to our file upload POST request.
     */
    protected static void callBridge (
        String id, String mediaHash, int mimeType, int constraint, int width, int height)
    {
        // for some reason the strings that come in from JavaScript are not "real" and if we just
        // pass them straight on through to GWT, freakoutery occurs (of the non-hand-waving
        // variety); so we convert them hackily to GWT strings here
        String fid = "" + id;
        String fhash = "" + mediaHash;
        _singleton.setHash(fid, fhash, mimeType, constraint, width, height);
    }

    /**
     * This is called from our magical JavaScript method by JavaScript code received from the
     * server to display an internal error message to the user.
     */
    protected static void uploadError ()
    {
        MsoyUI.error(_emsgs.errUploadError());
    }

    /**
     * This is called from our magical JavaScript method by JavaScript code received from the
     * server to display a friendly message to the user that the upload was too large.
     */
    protected static void uploadTooLarge ()
    {
        MsoyUI.error(_emsgs.errUploadTooLarge());
    }

    /**
     * Called when the user clicks the "save" button to commit their edits or create a new item.
     */
    protected void commitEdit ()
    {
        try {
            prepareItem();
        } catch (Exception e) {
            MsoyUI.error(e.getMessage());
            return;
        }

        // make sure the item is consistent
        if (_item == null || !_item.isConsistent()) {
            MsoyUI.error(_emsgs.editorNotConsistent());
            return;
        }

        if (_item.itemId == 0) {
            _stuffsvc.createItem(_item, _parentItem, new MsoyCallback<Item>() {
                public void onSuccess (Item item) {
                    MsoyUI.info(_emsgs.msgItemCreated());
                    _parent.editComplete(item);
                }
            });

        } else {
            _stuffsvc.updateItem(_item, new MsoyCallback<Void>() {
                public void onSuccess (Void result) {
                    MsoyUI.info(_emsgs.msgItemUpdated());
                    _parent.editComplete(_item);
                }
            });
        }
    }

    /**
     * Called when the user clicks create or update. The editor should flush any interface element
     * settings back to the item being edited and return null if the item is ready for editing. It
     * can throw an exception with a message to display to the user if the commit should be aborted
     * due to invalid or missing data.
     */
    protected void prepareItem ()
        throws Exception
    {
    }

    /**
     * A convenience method for attaching a textbox directly to a field in the item to be edited.
     *
     * TODO: If you paste text into the field, this doesn't detect it.
     */
    protected Widget bind (Widget widget, final Binder binder)
    {
        if (widget instanceof TextBoxBase) {
            final TextBoxBase textbox = (TextBoxBase)widget;
            textbox.addKeyboardListener(new KeyboardListenerAdapter() {
                @Override public void onKeyPress (Widget sender, char keyCode, int mods) {
                    if (_item != null) {
                        DeferredCommand.addCommand(new Command() {
                            public void execute () {
                                binder.textUpdated(textbox.getText());
                            }
                        });
                    }
                }
            });

        } else if (widget instanceof ListBox) {
            ((ListBox)widget).addChangeListener(new ChangeListener() {
                public void onChange (Widget sender) {
                    if (_item != null) {
                        binder.valueChanged();
                    }
                }
            });
        }
        return widget;
    }

    /**
     * Makes sure that the specified text is not null or all-whitespace and is less than or equal
     * to the specified maximum length.  Usually used in {@link #prepareItem}.
     */
    protected static boolean nonBlank (String text, int maxLength)
    {
        if (text == null) {
            return false;
        }
        int len = text.trim().length();
        return (len > 0) && (len <= maxLength);
    }

    /**
     * This wires up a sensibly named function that our POST response JavaScript code can call.
     */
    protected static native void configureBridge () /*-{
        $wnd.setHash = function (id, hash, type, constraint, width, height) {
           @client.editem.ItemEditor::callBridge(Ljava/lang/String;Ljava/lang/String;IIII)(id, hash, type, constraint, width, height);
        };
        $wnd.uploadError = function () {
           @client.editem.ItemEditor::uploadError()();
        };
        $wnd.uploadTooLarge = function () {
           @client.editem.ItemEditor::uploadTooLarge()();
        };
    }-*/;

    /** The type of items we're editing here. */
    protected byte _type;
    protected EditorHost _parent;

    protected Item _item;
    protected ItemIdent _parentItem;

    protected TextBox _name;
    protected LimitedTextArea _description;
    protected Button _esubmit;

    protected AbsolutePanel _header;
    protected StyledTabPanel _tabs;
    protected FlexTable _currentTab;

    protected FlexTable _content;

    protected Map<String, ItemMediaUploader> _uploaders = new HashMap<String, ItemMediaUploader>();

    protected static ItemEditor _singleton;

    protected static final ShellMessages _cmsgs = GWT.create(ShellMessages.class);
    protected static final EditemMessages _emsgs = GWT.create(EditemMessages.class);
    protected static final DynamicLookup _dmsgs = GWT.create(DynamicLookup.class);
    protected static final StuffServiceAsync _stuffsvc = (StuffServiceAsync)
        ServiceUtil.bind(GWT.create(StuffService.class), StuffService.ENTRY_POINT);

    protected static final String TYPE_IMAGE = "image";
    protected static final String TYPE_AUDIO = "audio";
    protected static final String TYPE_VIDEO = "video";
    protected static final String TYPE_FLASH = "flash";
    protected static final String TYPE_CODE = "code";
    protected static final String TYPE_ANY = "any";
}
