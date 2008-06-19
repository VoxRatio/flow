//
// $Id$

package client.editem;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.Pet;
import com.threerings.msoy.item.data.all.MediaDesc;

import client.shell.CShell;

/**
 * A class for creating and editing {@link Pet} digital items.
 */
public class PetEditor extends ItemEditor
{
    @Override // from ItemEditor
    public void setItem (Item item)
    {
        super.setItem(item);
        _pet = (Pet)item;
    }

    @Override // from ItemEditor
    public Item createBlankItem ()
    {
        return new Pet();
    }

    @Override // from ItemEditor
    protected void addFurniUploader ()
    {
        // pets are special; their furni media are their primary media
        addSpacer();
        addRow(CShell.emsgs.petLabel(), createFurniUploader(false, new MediaUpdater() {
            public String updateMedia (String name, MediaDesc desc, int width, int height) {
                if (!isValidPrimaryMedia(desc)) {
                    return CShell.emsgs.errPetNotFlash();
                }
                _item.furniMedia = desc;
                return null;
            }
        }), CShell.emsgs.petTip());
    }

    protected Pet _pet;
}
