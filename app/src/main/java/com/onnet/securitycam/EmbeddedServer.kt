package com.onnet.securitycam

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream

class EmbeddedServer(private val port: Int = 8080, private val streamer: CameraStreamer) : NanoWSD(port) {
        override fun serveHttp(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
                val uri = session?.uri ?: "/"
                return when (uri) {
                        "/" -> newFixedLengthResponse(Status.OK, "text/html", indexHtml)
                        "/frame.jpg" -> serveJpeg()
                        else -> newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not found")
                }
        }

        private fun serveJpeg(): NanoHTTPD.Response {
                val jpeg = streamer.getLatestJpeg()
                return if (jpeg != null) {
                        newFixedLengthResponse(Status.OK, "image/jpeg", ByteArrayInputStream(jpeg), jpeg.size.toLong())
                } else {
                        newFixedLengthResponse(Status.NO_CONTENT, "text/plain", "No frame yet")
                }
        }

        override fun openWebSocket(iHTTPSession: NanoHTTPD.IHTTPSession?): NanoWSD.WebSocket {
                return object : WebSocket(iHTTPSession) {
                        override fun onOpen() {}
                        override fun onClose() {}
                        override fun onMessage(message: WebSocketFrame?) {}
                        override fun onPong(message: WebSocketFrame?) {}
                        override fun onException(e: Exception?) { e?.printStackTrace() }
                }
        }

        companion object {
                private const val indexHtml = """
                        <!doctype html>
                        <html>
                        <head>
                            <meta charset=\"utf-8\"> 
                            <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"> 
                            <title>SecurityCam</title>
                        </head>
                        <body>
                            <h1>SecurityCam Stream</h1>
                            <img id=\"stream\" src=\"/frame.jpg\" style=\"max-width:100%;height:auto;\"/>
                            <div>
                                <button id=\"btnToggle\">Toggle Camera</button>
                                <button id=\"btnAudio\">Toggle Audio</button>
                            </div>
                            <script>
                                let audioEnabled = false;
                                let ws;
                                document.getElementById('btnToggle').addEventListener('click', ()=>{ fetch('/toggleCamera'); });
                                document.getElementById('btnAudio').addEventListener('click', ()=>{
                                    audioEnabled = !audioEnabled;
                                    if(audioEnabled) startAudio(); else stopAudio();
                                });

                                function startAudio(){
                                    ws = new WebSocket('ws://' + location.host + '/');
                                    ws.binaryType = 'arraybuffer';
                                    ws.onmessage = (evt)=>{
                                        // audio binary data — would need decoding; placeholder
                                        console.log('audio chunk', evt.data);
                                    }
                                }
                                function stopAudio(){ if(ws) ws.close(); }

                                setInterval(()=>{ const img = document.getElementById('stream'); if(img){ img.src = '/frame.jpg?ts=' + Date.now(); } }, 200);
                            </script>
                        </body>
                        </html>
                """
        }
}

