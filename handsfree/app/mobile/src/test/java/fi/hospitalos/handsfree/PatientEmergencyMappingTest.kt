package fi.hospitalos.handsfree
import org.junit.Assert.*
import org.junit.Test
class PatientEmergencyMappingTest {
 @Test fun exactAuthoredPatientFieldsSurviveMappingAndMalformedEntriesAreNotInvented(){
  val data=mapOf("id" to "demo-a","name" to "Demo A","encounterId" to "enc-a","room" to "12","admissionReason" to "Observation","allergies" to listOf("Reported allergy"),"medications" to listOf(mapOf("name" to "Amlodipine","dose" to "5 mg","route" to "Oral","schedule" to "Daily","prescribedBy" to "Dr Demo","prescribedAt" to "2026-11-14T06:00:00Z"),mapOf("name" to "Incomplete")),"careHistory" to listOf(mapOf("kind" to "procedure","description" to "BP taken","author" to "Ben","at" to "2026-11-14T08:00:00Z"),mapOf("kind" to "medication","description" to "Missing author")))
  val p=WardSnapshot.patient(data)!!;assertEquals("Observation",p.admissionReason);assertEquals(listOf("Reported allergy"),p.allergies);assertEquals(1,p.medications.size);assertEquals("Dr Demo",p.medications.single().prescribedBy);assertEquals(1,p.careHistory.size);assertEquals("Ben",p.careHistory.single().author)
 }
 @Test fun incompleteIsbarRemainsUnavailableAndInvalidEmergencyMetadataIsRejected(){val data=mapOf("id" to "e","from" to "ben","room" to "8","kind" to "code-blue","status" to "active","revision" to 1,"createdAt" to "2026-11-14T12:00:00Z","updatedAt" to "2026-11-14T12:00:00Z","response" to "acknowledged","isbar" to mapOf("identify" to "Demo patient"));assertNull(WardSnapshot.emergency(data)!!.isbar);assertNull(WardSnapshot.emergency(data+("createdAt" to "yesterday")));assertNull(WardSnapshot.emergency(data+("revision" to -1)));assertNull(WardSnapshot.emergency(data+("response" to "assumed")))}
}
