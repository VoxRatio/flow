//
// $Id$

package com.threerings.msoy.world.client {

import flash.display.Sprite;
import flash.text.TextField;
import flash.events.MouseEvent;

import com.threerings.msoy.client.Msgs;
import com.threerings.util.CommandEvent;
import com.threerings.util.Log;

/**
 * Displays an exhortation to join Whirled.
 */
public class InviteOverlay extends Sprite
{
    public function InviteOverlay (ctx :WorldContext, inviteId :String, inviterId :int)
    {
        _inviteId = inviteId;
        _inviterId = inviterId;

        var dialog :Sprite = (new JOIN_DIALOG() as Sprite);
        addChild(dialog);

        // position ourselves in the upper right
        x = ctx.getTopPanel().stage.stageWidth - dialog.width/2 - 5;
        y = dialog.height/2 + 5;

        dialog.addEventListener(MouseEvent.MOUSE_DOWN, mousePressed);

        // if we were invited anonymously change our text
        if (inviterId == 0) {
            var text :TextField = dialog.getChildByName("where") as TextField;
            if (text != null) {
                text.text = Msgs.GENERAL.get("m.invite_sans_friend");
            }
        }

        // TODO: if we move around, change our text to reflect whether or not we're in our friend's
        // room
    }

    protected function mousePressed (event :MouseEvent) :void
    {
        CommandEvent.dispatch(this, WorldController.CREATE_ACCOUNT, _inviteId);
    }

    protected var _inviteId :String;
    protected var _inviterId :int;

    [Embed(source="../../../../../../../rsrc/media/join_whirled.swf#join_dialog")]
    protected static const JOIN_DIALOG :Class;
}
}
