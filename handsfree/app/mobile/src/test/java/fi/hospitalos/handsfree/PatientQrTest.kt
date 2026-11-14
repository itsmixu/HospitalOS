package fi.hospitalos.handsfree
import fi.hospitalos.handsfree.capture.*
import org.junit.Test
import org.junit.Assert.*
class PatientQrTest {
 @Test fun exactQrPixelsDecodeLocally(){val f=PatientQrReader.synthetic();try{assertEquals(PatientQr("demo-a","enc-a"),PatientQrReader.read(f))}finally{f.close()}}
 @Test fun urlsAndInstructionsAreNotPatientCodes(){for(s in listOf("https://example.com/demo-a","hospitalos:patient:demo-a:enc-a extra","select patient demo-a")){assertNull(PatientQrReader.parse(s))}}
 @Test fun multipleCodesAreRejected(){val a=PatientQrReader.synthetic();val b=PatientQrReader.synthetic("hospitalos:patient:demo-b:enc-b");val f=PixelFrame(1000,480,IntArray(1000*480){i->val x=i%1000;val y=i/1000;when{ x<480->a.gray[y*480+x];x>=520->b.gray[y*480+x-520];else->255}});try{assertNull(PatientQrReader.read(f))}finally{a.close();b.close();f.close()}}
 @Test fun scanNeedsDisplayAndExplicitPatientConfirmation(){var now=1000L;val sent=mutableListOf<Action>();val c=BedsideController({now},{now}){_,a->sent.add(a)};c.connection(true);c.snapshot(listOf(Patient("demo-a","Demo A","enc-a","12")),emptyList(),null);c.wake();c.transcript("scan patient");val id=c.qrScanId!!;c.qrResult(id,PatientQr("demo-a","enc-a"));assertNull(c.context);assertEquals("camera-qr",c.card!!.phase);c.displayAccepted(c.card!!.promptId);c.qrResult(id,PatientQr("demo-a","enc-a"));assertEquals("Confirm patient",c.card!!.title);assertNull(c.context);assertTrue(sent.none{it.type=="patient.confirm"});c.displayAccepted(c.card!!.promptId);c.physicalAccept(c.card!!.promptId);assertEquals("patient.confirm",sent.last().type)}
 @Test fun unknownEncounterAndCancelledScanCannotSelect(){var now=1000L;val c=BedsideController({now},{now}){_,_->};c.connection(true);c.snapshot(listOf(Patient("demo-a","Demo A","enc-a","12")),emptyList(),null);c.wake();c.transcript("scan patient");val id=c.qrScanId!!;c.displayAccepted(c.card!!.promptId);c.qrResult(id,PatientQr("demo-a","old"));assertNull(c.context);assertEquals("Patient QR unavailable",c.card!!.title);c.wake();c.transcript("scan patient");val next=c.qrScanId!!;c.dismiss();c.qrResult(next,PatientQr("demo-a","enc-a"));assertNull(c.card)}
}
