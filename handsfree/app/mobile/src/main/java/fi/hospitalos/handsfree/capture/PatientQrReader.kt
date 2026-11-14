package fi.hospitalos.handsfree.capture
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeWriter

data class PatientQr(val patientId:String,val encounterId:String)
object PatientQrReader {
 fun parse(text:String):PatientQr?=Regex("hospitalos:patient:([a-z0-9-]{1,40}):([a-z0-9-]{1,40})").matchEntire(text)?.let{PatientQr(it.groupValues[1],it.groupValues[2])}
 fun read(frame:PixelFrame):PatientQr? {
  val bytes=ByteArray(frame.gray.size){frame.gray[it].toByte()}
  return try {
   val bitmap=BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(bytes,frame.width,frame.height,0,0,frame.width,frame.height,false)))
   val codes=QRCodeMultiReader().decodeMultiple(bitmap,mapOf(DecodeHintType.TRY_HARDER to true))
   if(codes.size==1)parse(codes.single().text)else null
  }catch(e:ReaderException){null}finally{bytes.fill(0)}
 }
 fun synthetic(text:String="hospitalos:patient:demo-a:enc-a"):PixelFrame {
  val m=QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,480,480)
  return PixelFrame(480,480,IntArray(480*480){if(m[it%480,it/480])0 else 255})
 }
}
