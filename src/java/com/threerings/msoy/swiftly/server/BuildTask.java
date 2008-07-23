//
// $Id$

package com.threerings.msoy.swiftly.server;

import com.threerings.presents.client.InvocationService.ResultListener;

import com.threerings.msoy.data.all.MemberName;

import com.threerings.msoy.swiftly.data.BuildResult;
import com.threerings.msoy.swiftly.server.build.BuildArtifact;

/** Handles a request to build our project. */
public class BuildTask extends AbstractBuildTask
{
    public BuildTask (ProjectRoomManager manager, MemberName member, ResultListener listener)
    {
        super(manager, member, listener);
    }

    @Override // from CommonBuildTask
    public void publishResult (final BuildResult result)
    {
        _omgr.postRunnable(new Runnable() {
            public void run() {
                _listener.requestProcessed(result);
            }
        });
    }

    @Override // from CommonBuildTask
    public void processArtifact (final BuildArtifact artifact)
    {
        // BuildTask does nothing with the artifact
    }
}
