package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SurveyPolicyTest {
    private val offset=Offset(.5f,0f,.5f,0f,-500,0)
    @Test fun sixRealVisitsRequired(){assertEquals(listOf("A" to 1,"B" to 1,"C" to 1,"C" to 2,"B" to 2,"A" to 2),SurveyPolicy.order)}
    @Test fun firstVisitIsA(){assertEquals("A" to 1,SurveyPolicy.next(emptyList()))}
    @Test fun firstRoundDoesNotCompleteTest(){val v=listOf("A","B","C").map {Visit(it,1,it,"env",offset,true)};assertEquals("C" to 2,SurveyPolicy.next(v))}
    @Test fun skipEvidenceDetected(){assertTrue(SurveyPolicy.synthetic("skip-A-1-123"))}
    @Test fun DuplicatedRoundsDetected(){assertTrue(SurveyPolicy.synthetic("some-uuid-r2"))}
    @Test fun genuineTapRetained(){assertFalse(SurveyPolicy.synthetic("7e66ef54-a86a-4fdf-98ba-ec570e86b90a"))}
    @Test fun stableLiveCaptureAccepted(){assertTrue(SurveyPolicy.canSave(true,true,offset,true,5000))}
    @Test fun missingOffsetRejected(){assertFalse(SurveyPolicy.canSave(true,true,null,true,5000))}
    @Test fun movingCaptureRejected(){assertFalse(SurveyPolicy.canSave(true,false,offset,true,5000))}
    @Test fun staleEngineRejected(){assertFalse(SurveyPolicy.canSave(true,true,offset,false,5000))}
    @Test fun oldTapRejected(){assertFalse(SurveyPolicy.canSave(true,true,offset,true,300001))}
    @Test fun appOnlyCaptureRejected(){assertFalse(SurveyPolicy.canSave(false,true,offset,true,5000))}
}
