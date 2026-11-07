package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SavedSetupPolicyTest {
    private fun restore(vararg denied:Int):Boolean {
        val flags=BooleanArray(7){it !in denied}
        return SavedSetupPolicy.canRestore(flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6])
    }
    @Test fun matchingConfirmedLayoutCanBeReused(){assertTrue(restore())}
    @Test fun unconfirmedLayoutCannotBeRestored(){assertFalse(restore(0))}
    @Test fun removedWidgetCannotBeRestored(){assertFalse(restore(1))}
    @Test fun resizedOrInvalidMeasurementCannotBeRestored(){assertFalse(restore(2))}
    @Test fun displayOrLauncherChangeCannotBeRestored(){assertFalse(restore(3))}
    @Test fun anotherPageCannotRestoreTarget(){assertFalse(restore(4))}
    @Test fun movingPageCannotRestoreTarget(){assertFalse(restore(5))}
    @Test fun overviewZoomCannotRestoreTarget(){assertFalse(restore(6))}
    @Test fun confirmedSavedFileCanPlay(){assertTrue(SavedSetupPolicy.canPlay(true,false,false,false))}
    @Test fun missingFileCannotPlay(){assertFalse(SavedSetupPolicy.canPlay(false,false,false,false))}
    @Test fun importingFileCannotPlayOldFile(){assertFalse(SavedSetupPolicy.canPlay(true,true,false,false))}
    @Test fun unconfirmedPreviewCannotPlayOldFile(){assertFalse(SavedSetupPolicy.canPlay(true,false,true,false))}
    @Test fun failedChoiceCannotPlayOldFile(){assertFalse(SavedSetupPolicy.canPlay(true,false,false,true))}
}
