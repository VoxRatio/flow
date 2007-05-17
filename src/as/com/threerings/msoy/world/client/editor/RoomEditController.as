//
// $Id$

package com.threerings.msoy.world.client.editor {

import mx.controls.Button;
import flash.events.Event;
import flash.events.KeyboardEvent;
import flash.events.MouseEvent;
import flash.geom.Point;
import flash.geom.Rectangle;
import flash.ui.Keyboard;

import com.threerings.flash.Vector3;
import com.threerings.msoy.client.WorldContext;
import com.threerings.msoy.world.client.ClickLocation;
import com.threerings.msoy.world.client.FurniSprite;
import com.threerings.msoy.world.client.DecorSprite;
import com.threerings.msoy.world.client.MsoySprite;
import com.threerings.msoy.world.client.RoomController;
import com.threerings.msoy.world.client.RoomMetrics;
import com.threerings.msoy.world.client.RoomView;
import com.threerings.msoy.world.client.updates.SceneUpdateAction;
import com.threerings.msoy.world.client.updates.FurniUpdateAction;
import com.threerings.msoy.world.data.FurniData;
import com.threerings.msoy.world.data.MsoyLocation;
import com.threerings.msoy.world.data.MsoyScene;

/**
 * Controller for the room editing panel.
 */
public class RoomEditController
{
    // The controller processes mouse movement in the room, based on the current editing action
    // ("action"), and the particular stage this action has reached so far ("phase"). Not all
    // actions have all of the phases; movement includes all of them, but deleting only some.
    // Any mouse input needs to be processed based on the < action, phase, input type > triple,
    // which happens in two stages: first, an appropriate handler is found based on the 
    // < phase, input type > combination (see tables: MOVES, CLICKS, HOVERS), and then
    // this handler can do something special based on action type.


    /**
     * ACTION_* constants describe current action being performed.
     */
    public static const ACTION_ROOM :String = "Edit Room";
    public static const ACTION_MOVE :String = "Edit Move";
    public static const ACTION_SCALE :String = "Edit Scale";
    public static const ACTION_PREFS :String = "Edit Preferences";
    public static const ACTION_DELETE :String = "Edit Delete";
    public static const ACTION_UNDO :String = "Edit Undo";
    
    /**
     * PHASE_* constants describe the current editing phase. Each action can go through
     * the following phases:
     *   init    - resets targeting (all actions)
     *   acquire - target is acquired for editing mode (move, scale, delete, prefs)
     *   modify  - target is modified based on movement (move, scale)
     *   commit  - acquisition/modification changes are processed (move, scale, delete, prefs)
     *   done    - editing is finalized (all actions)
     * If a new action is selected before the /done/ phase, the current phase is rolled back
     * to the latest phase valid for the new type of action. For example, if we're in the
     * /modify/ phase of a move action, switching to a delete action will revert us to the
     * /acquire/ phase, while switching to scale action won't modify the phase at all.
     */
    public static const PHASE_INIT    :int = 0;
    public static const PHASE_ACQUIRE :int = 1;
    public static const PHASE_MODIFY  :int = 2;
    public static const PHASE_COMMIT  :int = 3;
    public static const PHASE_DONE    :int = 4;

    public static const PHASEACTIONS :Array =
        [ /* init */    [ ACTION_MOVE, ACTION_SCALE, ACTION_DELETE, ACTION_PREFS,
                              ACTION_UNDO, ACTION_ROOM ],
          /* acquire */ [ ACTION_MOVE, ACTION_SCALE, ACTION_DELETE, ACTION_PREFS ],
          /* modify */  [ ACTION_MOVE, ACTION_SCALE ],
          /* commit */  [ ACTION_MOVE, ACTION_SCALE, ACTION_DELETE, ACTION_PREFS ],
          /* done */    [ ACTION_MOVE, ACTION_SCALE, ACTION_DELETE, ACTION_PREFS,
                              ACTION_UNDO, ACTION_ROOM ] ];

    // for debugging only
    protected static const PHASENAMES :Array = [ "init", "acquire", "modify", "commit", "done" ];

    /** Drawing properties for the acquire phase. */
    protected static const ACQUIRE_DANGEROUS_ACTIONS :Array = [ ACTION_DELETE ];
    protected static const ACQUIRE_STEM_ACTIONS :Array = [ ACTION_MOVE ];

    /** Editing mode preferences. */
    public var moveYAxisOnly :Boolean;
    public var moveZAxisOnly :Boolean;
    
    public function RoomEditController (ctx :WorldContext, panel :RoomEditPanel)
    {
        _ctx = ctx;
        _panel = panel;
    }

    public function init () :void
    {
        _panel.roomView.stage.addEventListener(KeyboardEvent.KEY_DOWN, handleKeyboard);
        _panel.roomView.stage.addEventListener(KeyboardEvent.KEY_UP, handleKeyboard);
        _panel.roomView.setEditing(true);
        
        _settings = new SettingsController(_ctx, this);
        _itemprefs = new ItemPreferencesPanel(_ctx, this, roomCtrl);
    }

    public function deinit () :void
    {
        switchToPhase(PHASE_DONE); // just end whatever was going on, skipping commit

        _itemprefs.close();
        _itemprefs = null;
        
        _settings.finish(false);
        _settings = null;

        _panel.roomView.setEditing(false);
        _panel.roomView.stage.removeEventListener(KeyboardEvent.KEY_DOWN, handleKeyboard);
        _panel.roomView.stage.removeEventListener(KeyboardEvent.KEY_UP, handleKeyboard);
    }

    /** Returns current editing phase, as one of the PHASE_* constants. */
    public function get currentPhase () :int
    {
        return _currentPhase;
    }

    /** Returns a reference to the current room view. */
    public function get roomView () :RoomView
    {
        return _panel.roomView;
    }

    /** Returns a reference to the current room controller. */
    public function get roomCtrl () :RoomController
    {
        return _panel.roomView.getRoomController();
    }

    // Panel accessors

    /** Receives mouse updates from the panel, with x, y values in scene coordinates. */
    public function mouseMove (x :Number, y :Number) :void
    {
        (MOVES[_currentPhase] as Function)(x, y);
    }

    /** Receives mouse updates from the panel, if a sprite is currently being selected. */
    public function mouseOverSprite (sprite :MsoySprite) :void
    {
        (HOVERS[_currentPhase] as Function)(sprite);
    }        

    /** Processes mouse clicks during target acquisition. */
    public function mouseClick (sprite :MsoySprite, event :MouseEvent) :void
    {
        (CLICKS[_currentPhase] as Function)(sprite, event);
    }

    /** Handle click on one of the action buttons. */
    public function handleActionSelection (action :String, button :Button, def :Object) :void
    {
        // when momentary buttons are pressed, or when toggle buttons are clicked on,
        // update the info bar and start the action. when toggle buttons are clicked off,
        // force current action to end.
        
        var actionExists :Boolean = (! button.toggle) || button.selected;
        if (actionExists) {
            _panel.setInfoLabel (def);
            processAction(action);
        } else {
            _panel.setInfoLabel (null);
            switchToPhase(PHASE_DONE);
        }
    }

    // Helpers

    /**
     * Sends an update to the server. /toRemove/ will be removed, and /toAdd/ added.
     */
    public function updateFurni (toRemove :FurniData, toAdd :FurniData) :void
    {
        roomCtrl.applyUpdate(new FurniUpdateAction(_ctx, toRemove, toAdd));
        _panel.updateUndoButton(true);
    }

    /**
     * Sends an update to the server. /oldScene/ will be updated with data from the /newScene/.
     */
    public function updateScene (oldScene :MsoyScene, newScene :MsoyScene) :void
    {
        roomCtrl.applyUpdate(new SceneUpdateAction(_ctx, oldScene, newScene));
        _panel.updateUndoButton(true);
    }
  
    /**
     * Keeps track of the different keys used to modify edit settings.
     */
    protected function handleKeyboard (event :KeyboardEvent) :void
    {
        // this is very ad hoc right now. do we have any big plans for keyboard shortcuts?
        if (_currentAction == ACTION_MOVE && _currentPhase == PHASE_MODIFY) {
            if (event.type == KeyboardEvent.KEY_DOWN) {
                moveYAxisOnly = (event.keyCode == Keyboard.SHIFT);
                moveZAxisOnly = (event.keyCode == Keyboard.CONTROL);
            }
            if (event.type == KeyboardEvent.KEY_UP) {
                moveYAxisOnly = moveYAxisOnly && !(event.keyCode == Keyboard.SHIFT);
                moveZAxisOnly = moveZAxisOnly && !(event.keyCode == Keyboard.CONTROL);
            }
        }
    }

    /**
     * Handles clicking on one of the target-less actions. After calling this function,
     * they fall through to their /done/ phase.
     */
    protected function handleActionsWithoutTargets () :void
    {
        // non-target buttons (room settings, undo) perform their actions here.
        switch (_currentAction) {
        case ACTION_ROOM:
            _settings.start();
            break;
        case ACTION_UNDO:
            // undo the last action, and set undo button's enabled state appropriately
            _panel.updateUndoButton(roomCtrl.undoLastUpdate());
            break;
        }
    }

    
    // Phase: init

    protected function doInit () :void
    {
        handleActionsWithoutTargets();
        switchToPhase(nextPhase());
    }

    // Phase: acquire

    protected function startAcquire () :void
    {
        if (_currentTarget != null && _originalTargetData != null) {
            // somehow we ended up here after a target was already selected! in this case,
            // restore the target to what it used to be, because we'll acquire a fresh one
            _currentTarget.update(_originalTargetData);
            _currentTarget = null;
            _originalTargetData = null;
        }

        if (_acquireCandidate != null) {
            _panel.clearFocus(_acquireCandidate);
            _acquireCandidate = null;
        }
    }

    protected function hoverAcquire (sprite :MsoySprite) :void
    {
        // treat non-furni sprites and decor sprites as if they were background
        if (! (sprite is FurniSprite) || sprite is DecorSprite) {
            sprite = null;
        }
        // and make sure it's not the same as what we hit on the last frame
        if (_acquireCandidate != sprite) {
            _panel.clearFocus(_acquireCandidate);
            _acquireCandidate = sprite as FurniSprite;

            var dangerous :Boolean = (ACQUIRE_DANGEROUS_ACTIONS.indexOf(_currentAction) != -1);
            var drawStem :Boolean = (ACQUIRE_STEM_ACTIONS.indexOf(_currentAction) != -1);
            var highlightColor :uint = dangerous ? 0xff0000 : 0xffffff;

            _panel.updateFocus(_acquireCandidate, _currentAction, drawStem, highlightColor);
        }
    }

    protected function clickAcquire (sprite :MsoySprite, event :MouseEvent) :void
    {
        // if we clicked on something, transition to the next phase, otherwise keep trying
        if (_acquireCandidate != null) {
            switchToPhase(nextPhase());
        }
    }

    protected function endAcquire () :void
    {
        _panel.clearFocus(_acquireCandidate);

        // remember the target
        _currentTarget = _acquireCandidate;
        _acquireCandidate = null;

        if (_currentTarget != null) {
            _originalTargetData = _currentTarget.getFurniData().clone() as FurniData;
        }
    }

    // Phase: modify
    
    protected function startModify () :void
    {
        // if we haven't acquired a target, skip this phase, even if this action supports it
        if (_currentTarget == null) {
            switchToPhase(nextPhase());
            return;
        }

        // otherwise start modification functionality

        _modOriginalBounds = new Rectangle(
            _currentTarget.x, _currentTarget.y,
            _currentTarget.getActualWidth(), _currentTarget.getActualHeight());
        _modOriginalScale = new Point(
            _currentTarget.getMediaScaleX(), _currentTarget.getMediaScaleY());
        _modOriginalHotspot = _currentTarget.localToGlobal(_currentTarget.getLayoutHotSpot());
        _modOriginalMouse = new Point(_panel.roomView.stage.mouseX, _panel.roomView.stage.mouseY);
        
        var drawStem :Boolean = (ACQUIRE_STEM_ACTIONS.indexOf(_currentAction) != -1);
        _panel.updateFocus(_currentTarget, _currentAction, drawStem);
    }

    protected function moveModify (x :Number, y :Number) :void
    {
        processModify(x, y, false);

        var drawStem :Boolean = (ACQUIRE_STEM_ACTIONS.indexOf(_currentAction) != -1);
        _panel.updateFocus(_currentTarget, _currentAction, drawStem);
    }

    protected function clickModify (sprite :MsoySprite, event :MouseEvent) :void
    {
        processModify(event.stageX, event.stageY, true);

        switchToPhase(nextPhase());
    }
    
    protected function processModify (x :Number, y :Number, isClick :Boolean) :void
    {
        switch (_currentAction) {
        case ACTION_MOVE:
            moveFurni(_currentTarget, findNewFurniPosition(x, y), isClick);
            break;
        case ACTION_SCALE:
            scaleFurni(_currentTarget, x, y);
            break;
        }
    }
    
    protected function endModify () :void
    {
        _modOriginalBounds = null;
        _modOriginalScale = null;
        _modOriginalMouse = null;
        _modOriginalHotspot = null;
        _panel.clearFocus(_currentTarget);
    }

    // Phase: commit
    
    protected function startCommit () :void
    {
        var nextPhase :int = nextPhase();
        
        switch (_currentAction) {
        case ACTION_MOVE:  // falls through
        case ACTION_SCALE:
            // both the move and scale actions commit data immediately, then force a return
            // back to the init state, so the player can move or scale more objects. 
            if (_currentTarget != null && _originalTargetData != null) {
                updateFurni(_originalTargetData, _currentTarget.getFurniData());
            }
            nextPhase = PHASE_INIT; // force a loop
            break;
        case ACTION_DELETE:
            // delete the old object
            if (_originalTargetData != null) {
                updateFurni(_originalTargetData, null);
            }
            nextPhase = PHASE_INIT; // force a loop
            break;
        case ACTION_PREFS:
            // display/refresh a properties window
            if (_currentTarget != null) {
                displayProperties(_currentTarget);
            }
            break;
        }

        switchToPhase(nextPhase);
    }

    protected function endCommit () :void
    {
        // clean up state variables
        _currentTarget = null;
        _originalTargetData = null;
    }

    // Phase: done
    
    protected function doDone () :void
    {
        // no phase switches here. :)
    }


    // Movement only functions
    
    protected function moveFurni (
        target :FurniSprite, loc :MsoyLocation, updateFurniData :Boolean) :void
    {
        target.setLocation(loc);
        if (updateFurniData) {
            target.getFurniData().loc = loc;
        }
    }

    protected function findNewFurniPosition (x :Number, y :Number) :MsoyLocation
    {
        x -= (_modOriginalMouse.x - _modOriginalHotspot.x);
        y -= (_modOriginalMouse.y - _modOriginalHotspot.y);
        
        var anchor :MsoyLocation = ((moveYAxisOnly || moveZAxisOnly) && _currentTarget != null) ?
            _currentTarget.getLocation() : null;
        
        var direction :Vector3 = null;
        if (moveYAxisOnly) {
            direction = RoomMetrics.N_UP;
        }
        if (moveZAxisOnly) {
            direction = RoomMetrics.N_AWAY;
        }
            
        var cloc :ClickLocation = _panel.roomView.layout.pointToFurniLocation(
            x, y, anchor, direction);
        
        return cloc.loc;
    }

    // Scaling only functions

    protected function scaleFurni (furni :FurniSprite, x :Number, y :Number) :void
    {
        // find pixel distance from hotspot to the current and the original mouse pointer
        var mouse :Point = new Point(x, y);
        var newoffset :Point = mouse.subtract(_modOriginalHotspot);
        var oldoffset :Point = _modOriginalMouse.subtract(_modOriginalHotspot);

        // find scaling factor based on mouse movement
        var ratioX :Number = newoffset.x / oldoffset.x;
        var ratioY :Number = newoffset.y / oldoffset.y;

        furni.setMediaScaleX(ratioX * _modOriginalScale.x);
        furni.setMediaScaleY(ratioY * _modOriginalScale.y);
    }

    // Item properties display

    /**
     * Opens an editing window for furni properties. If one is already open, it just gets
     * refreshed with new furni info.
     */
    protected function displayProperties (sprite :FurniSprite) :void
    {
        if (! _itemprefs.isOpen) {
            _itemprefs.open();
        }
        _itemprefs.update(sprite.getFurniData());
    }
    
    // Phase and action helpers

    /** Returns true if the given phase supports the given action. */
    protected function phaseSupports (phase :int, action :String) :Boolean {
        var actions :Array = PHASEACTIONS[phase];
        return (actions.indexOf(action) != -1);
    }
    
    /**
     * Given some phase, advances through the phase list forward or backward, searching for
     * the next phase supported by the specified action. Search direction is specified by
     * the /reverse/ flag (forward if false, backward if true).
     */
    protected function revertPhase (action :String) :int
    {
        // if we're done, just restart
        if (_currentPhase == PHASE_DONE) {
            return PHASE_INIT;
        }

        // okay, let's search manually, starting from *current* phase 
        var last :int = _currentPhase; 
        while (last > PHASE_INIT) {
            if (phaseSupports(last, action)) {
                return last;
            }
            last--;
        }
        return PHASE_INIT;
    }

    /**
     * Given current phase, finds the next phase for the current action.
     */
    protected function nextPhase () :int
    {
        var next :int = _currentPhase + 1; // start from next phase, and search forward
        while (next < PHASE_DONE) {
            if (phaseSupports(next, _currentAction)) {
                return next;
            }
            next++;
        }
        return PHASE_DONE;
    }
    
    /**
     * Start a completely new action. Finds the latest phase valid for this action,
     * and switches there.
     */        
    protected function processAction (action :String) :void
    {
        // get the latest phase supported by the current action
        var phase :int = revertPhase(action);
        _currentAction = action;
        switchToPhase(phase);
    }

    /** Switch to the new phase. */
    protected function switchToPhase (phase :int) :void
    {
        if (phase != _currentPhase) {
            (DEINITS[_currentPhase] as Function)();
            _currentPhase = phase;
            (INITS[_currentPhase] as Function)();
        }
    }

    
    protected const none :Function = function (... args) :void { };

    /** Mouse input handlers for all actions, indexed by PHASE_* value. */
    protected const MOVES   :Array = [ none,   none,          moveModify,   none,     none   ];
    protected const HOVERS  :Array = [ none,   hoverAcquire,  none,         none,     none   ];
    protected const CLICKS  :Array = [ none,   clickAcquire,  clickModify,  none,     none   ];

    /** Phase initialization and shutdown helpers, indexed by PHASE_* value. */
    protected const INITS   :Array = [ doInit, startAcquire,  startModify,  startCommit, doDone ];
    protected const DEINITS :Array = [ none,   endAcquire,    endModify,    endCommit,   none   ];

    protected var _ctx :WorldContext;
    protected var _panel :RoomEditPanel;
    
    /** During acquisition phase, points to the different sprites as the mouse hovers over them. */
    protected var _acquireCandidate :FurniSprite;

    /** Result of the acquisition phase, points to the acquired sprite. */
    protected var _currentTarget :FurniSprite;

    /** Result of the acquisition phase, contains the acquired sprite's original data. */
    protected var _originalTargetData :FurniData;

    /** Sprite size at the beginning of modifications.
     *  Only valid in the modification phase. */
    protected var _modOriginalBounds :Rectangle;
    /** Sprite scale at the beginning of modifications.
     *  Only valid in the modification phase. */
    protected var _modOriginalScale :Point;
    /** Sprite hotspot in stage coordinates at the beginning of modifications.
     *  Only valid in the modification phase. */
    protected var _modOriginalHotspot :Point;
    /** Mouse position at the beginning of modifications.
     *  Only valid in the modification phase. */
    protected var _modOriginalMouse :Point;

    protected var _currentAction :String;
    protected var _currentPhase :int = PHASE_DONE;

    protected var _settings :SettingsController;
    protected var _itemprefs :ItemPreferencesPanel;

}
}
