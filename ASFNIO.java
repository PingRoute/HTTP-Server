import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.util.Locale;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParamBean;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnectionFactory;
import org.apache.http.impl.nio.SSLNHttpServerConnectionFactory;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.entity.NFileEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.BasicAsyncResponseProducer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerRegistry;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;

public class ASFNIO {
public static void main(String[] args) throws Exception {
if(args.length<1) {
System.err.println("Please specify document root directory");
System.exit(1);
}
File docRoot = new File(args[0]);
int port = 80;
if(args.length >=2) {
port = Integer.parseInt(args[1]);
}
HttpParams parentParams = new BasicHttpParams();
HttpProtocolParamBean paramsBean = new HttpProtocolParamBean(parentParams);
paramsBean.setVersion(HttpVersion.HTTP_1_1);
paramsBean.setContentCharset("UTF-8");
HttpProcessor httpproc = HttpProcessorBuilder.create()
.add(new ResponseDate())
.add(new ResponseServer("Test/1.1"))
.add(new ResponseContent())
.add(new ResponseConnControl()).build();
HttpAsyncRequestHandlerRegistry registry = new HttpAsyncRequestHandlerRegistry();
registry.register("*", new HttpFileHandler(docRoot));
HttpAsyncService protocolHandler = new HttpAsyncService(httpproc, new DefaultConnectionReuseStrategy(), registry, parentParams) 
{
public void connected(final NHttpServerConnection conn) {
System.out.println(conn + ": connection open");
super.connected(conn);
}
public void closed(final NHttpServerConnection conn) {
System.out.println(conn + ": connection closed");
super.closed(conn);
}
};
//protocolHandler.setHandlerResolver(registry);
NHttpConnectionFactory<DefaultNHttpServerConnection> connFactory;
if(port == 8443) {
ClassLoader cl = ASFNIO.class.getClassLoader();
URL url = cl.getResource("my.keystore");
if(url == null) {
System.out.println("keystore not found");
System.exit(1);
}
KeyStore keystore = KeyStore.getInstance("jks");
keystore.load(url.openStream(), "secret".toCharArray());
KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
kmfactory.init(keystore, "secret".toCharArray());
KeyManager[] keymanagers = kmfactory.getKeyManagers();
SSLContext sslcontext = SSLContext.getInstance("TLS");
sslcontext.init(keymanagers, null, null);
connFactory = new SSLNHttpServerConnectionFactory(sslcontext, null, ConnectionConfig.DEFAULT);
} else { 
connFactory = new DefaultNHttpServerConnectionFactory(ConnectionConfig.DEFAULT);}
IOEventDispatch ioEventDispatch = new DefaultHttpServerIODispatch(protocolHandler, connFactory);
IOReactorConfig config = IOReactorConfig.custom()
.setIoThreadCount(1)
.setSoTimeout(3000)
.setConnectTimeout(3000)
.build();
ListeningIOReactor ioReactor = new DefaultListeningIOReactor(config);
try {
ioReactor.listen(new InetSocketAddress(port));
ioReactor.execute(ioEventDispatch);
} catch(InterruptedIOException ex) {
System.err.println("Interrupted");
} catch(IOException e) {
System.err.println("I/O error: " + e.getMessage());
}
System.out.println("Shutdown");
}
static class HttpFileHandler implements HttpAsyncRequestHandler<HttpRequest> {
private final File docRoot;
public HttpFileHandler(final File docRoot) {
super();
this.docRoot = docRoot;
}
public HttpAsyncRequestConsumer<HttpRequest>processRequest(final HttpRequest request, final HttpContext context) {
return new BasicAsyncRequestConsumer();
}
public void handle(final HttpRequest request, final HttpAsyncExchange httpexchange, final HttpContext context) throws HttpException, IOException {
HttpResponse response = httpexchange.getResponse();
handleInternal(request, response, context);
httpexchange.submitResponse(new BasicAsyncResponseProducer(response));
}
private void handleInternal(final HttpRequest request, final HttpResponse response, final HttpContext context) throws HttpException, IOException {
HttpCoreContext coreContext = HttpCoreContext.adapt(context);
String method = request.getRequestLine().getMethod().toUpperCase();
if(!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
throw new MethodNotSupportedException(method + " method not supported");
}
//final File file = new File(this.docRoot);
if(!docRoot.exists()) {
response.setStatusCode(HttpStatus.SC_NOT_FOUND);
NStringEntity entity = new NStringEntity("<html><body><h1>File" + docRoot.getPath()+" not found</h1></body></html>", ContentType.create("text/html", "UTF-8"));
response.setEntity(entity);
System.out.println("File " + docRoot.getPath() + " not found");
} else if(!docRoot.canRead() || docRoot.isDirectory()) {
response.setStatusCode(HttpStatus.SC_FORBIDDEN);
NStringEntity entity = new NStringEntity("<html><body><h1>Access denied</h1></body></html>", ContentType.create("text/html", "UTF-8"));
response.setEntity(entity);
System.out.println("Cannot read file " + docRoot.getPath());
} else {
NHttpConnection conn = coreContext.getConnection(NHttpConnection.class);
response.setStatusCode(HttpStatus.SC_OK);
NFileEntity body = new NFileEntity(docRoot, ContentType.create("text/html"));
response.setEntity(body);
System.out.println(conn + ": serving file " + docRoot.getPath());
}
}
}
}
