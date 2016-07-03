package controllers

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsDefined, JsUndefined}
import play.api.libs.ws.{WS, WSAuthScheme, WSClient}
import javax.inject._
import play.api._
import play.api.mvc._
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import play.api.Configuration

@Singleton
class HomeController @Inject() (
  ws: WSClient,
  conf: Configuration
) extends Controller {
  val MaxFileSize = 32 * 1024
  val WatsonUrl: String = conf.getString("watson.url").getOrElse(
    throw new IllegalStateException("Set watson.url in application.conf")
  ) + "/v1/recognize?model=ja-JP_NarrowbandModel"
  val WatsonUser: String = conf.getString("watson.username").getOrElse(
    throw new IllegalStateException("Set watson.username in application.conf")
  )
  val WatsonPassword: String = conf.getString("watson.password").getOrElse(
    throw new IllegalStateException("Set watson.password in application.conf")
  )

  def createWavFile(data: Path): Path = Files.write(
    Files.createTempFile(null, ".wav"), createWav(data)
  )

  def storeAscii(str: String, buf: Array[Byte], offset: Int): Int = {
    str.zipWithIndex.foreach { case (c, idx) =>
      buf(offset + idx) = (c & 0xff).asInstanceOf[Byte]
    }
    offset + str.length
  }

  def storeInt(bigEndian: Int, buf: Array[Byte], offset: Int): Int = {
    buf(offset) = (bigEndian & 0xff).asInstanceOf[Byte]
    buf(offset + 1) = ((bigEndian >> 8) & 0xff).asInstanceOf[Byte]
    buf(offset + 2) = ((bigEndian >> 16) & 0xff).asInstanceOf[Byte]
    buf(offset + 3) = ((bigEndian >> 24) & 0xff).asInstanceOf[Byte]
    offset + 4
  }

  def storeShort(bigEndian: Int, buf: Array[Byte], offset: Int): Int = {
    buf(offset) = (bigEndian & 0xff).asInstanceOf[Byte]
    buf(offset + 1) = ((bigEndian >> 8) & 0xff).asInstanceOf[Byte]
    offset + 2
  }

  def signEx(i: Int): Int = if ((i & 0x800) != 0) 0xfffff000 | i else i

  def createWav(data: Path): Array[Byte] = {
    //  0  char riff[4];
    //  4  int32_t len1;
    //  8  char wave[4];
    // 12  char fmt[4];
    // 16  int32_t formatSize;
    // 20  int16_t formatCode;
    // 22  int16_t channelCount;
    // 24  int32_t samplingRate;
    // 28  int32_t bytesPerSecond;
    // 32  int16_t bytesPerBlock;
    // 34  int16_t bitsPerSample;
    // 36  char data[4];
    // 40  int32_t len2;
    // 44  int16_t buf[];
    val fileSize = Files.size(data)
    if (fileSize > MaxFileSize)
      throw new Error("File size too long. (" + fileSize + ")")

    val in: Array[Byte] = Files.readAllBytes(data)
    val out = new Array[Byte](44 + in.length / 3 * 2 * 2)
    var idx = 0
    idx = storeAscii("RIFF", out, idx)
    idx = storeInt(out.length - 8, out, idx)
    idx = storeAscii("WAVE", out, idx)
    idx = storeAscii("fmt ", out, idx)
    idx = storeInt(16, out, idx)
    idx = storeShort(1, out, idx)
    idx = storeShort(1, out, idx)
    idx = storeInt(8000, out, idx)
    idx = storeInt(16000, out, idx)
    idx = storeShort(2, out, idx)
    idx = storeShort(16, out, idx)
    idx = storeAscii("data", out, idx)
    idx = storeInt(out.length - 126, out, idx)

    @tailrec def conv(offset: Int) {
      if (offset < in.length) {
        val b0 = 0xff & in(offset)
        val b1 = 0xff & in(offset + 1)
        val b2 = 0xff & in(offset + 2)

        idx = storeShort(signEx(((b1 & 0x0f) << 8) + b0) * 16, out, idx)
        idx = storeShort(signEx((b2 << 4) + ((b1 & 0xf0) >> 4)) * 16, out, idx)
        conv(offset + 3)
      }
    }

    conv(0)
    out
  }

  def index = Action.async { req =>
    val file: File = req.body.asRaw.get.asFile
    val temp: Path = Files.createTempFile(Paths.get("/tmp"), null, ".wav")

    Files.copy(file.toPath, temp, StandardCopyOption.REPLACE_EXISTING)
    val wav: Path = createWavFile(temp)
    println("wav: " + wav.toAbsolutePath)

    ws.url(
      WatsonUrl
    ).withAuth(
      WatsonUser, WatsonPassword, WSAuthScheme.BASIC
    ).withHeaders(
      "Content-Type" -> "audio/wav"
    ).post(
      wav.toFile
    ).map { response =>
      println("response: '" + response.json + "'")
      val transcript = ((response.json \ "results")(0) \ "alternatives")(0) \ "transcript" match {
        case JsDefined(v) => v.toString
        case undefined: JsUndefined => "Undefined"
      }

      println("transcript: '" + transcript + "'")
      val command = transcript.foldLeft(new StringBuilder) { (buf, c) =>
        if (c != ' ' && c != '"') buf.append(c)
        else buf
      }.toString

      println("command: '" + command + "'")
      Ok(if (command == "明るくして") "ON" else "OFF")
    }
  }
}
