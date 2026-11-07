package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SetupPolicyTest {
    @Test fun firstRunStartsAtBackground() { assertEquals(1,SetupPolicy.step(false,false,false)) }
    @Test fun existingWidgetCannotSkipBackgroundSetup() { assertEquals(1,SetupPolicy.step(false,false,true)) }
    @Test fun backgroundChoiceRequiresActualActivation() { assertEquals(2,SetupPolicy.step(true,false,false)) }
    @Test fun existingWidgetCannotSkipWallpaperActivation() { assertEquals(2,SetupPolicy.step(true,false,true)) }
    @Test fun activeWallpaperWithoutWidgetAsksForWidget() { assertEquals(3,SetupPolicy.step(true,true,false)) }
    @Test fun existingWorkingSetupResumesAtCapture() { assertEquals(4,SetupPolicy.step(false,true,true)) }
    private fun ready(active:Boolean=true,installed:Boolean=true,valid:Boolean=true,engine:Boolean=true,same:Boolean=true)=SetupPolicy.readiness(active,installed,valid,engine,same)
    @Test fun validBoundsWithWallpaperOffCannotStart() { assertEquals("WALLPAPER_OFF",ready(active=false)) }
    @Test fun previewSurfaceWithoutAppliedWallpaperCannotStart() { assertEquals("WALLPAPER_OFF",ready(active=false,engine=true)) }
    @Test fun removedWidgetCannotStart() { assertEquals("WIDGET_REMOVED",ready(installed=false)) }
    @Test fun missingOrInvalidBoundsCannotStart() { assertEquals("BOUNDS_MISSING",ready(valid=false)) }
    @Test fun selectedWallpaperWithoutEngineCannotStart() { assertEquals("ENGINE_NOT_READY",ready(engine=false)) }
    @Test fun oldEnvironmentCannotStart() { assertEquals("CAPTURE_STALE",ready(same=false)) }
    @Test fun readyRequiresAllPrerequisites() { assertEquals("READY",ready()) }
    @Test fun matchingConfirmedWidgetKeepsCompletion() { assertTrue(SetupPolicy.confirmationApplies(true,true,true,true)) }
    @Test fun removedWidgetCannotKeepCompletion() { assertFalse(SetupPolicy.confirmationApplies(false,true,true,true)) }
    @Test fun invalidatedWidgetCannotKeepCompletion() { assertFalse(SetupPolicy.confirmationApplies(true,false,true,true)) }
    @Test fun restartedWallpaperRequiresRechecking() { assertFalse(SetupPolicy.confirmationApplies(true,true,false,true)) }
    @Test fun movedWidgetCannotKeepOldCompletion() { assertFalse(SetupPolicy.confirmationApplies(true,true,true,false)) }
}
