package org.hum.nettyproxy.common.helper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.hum.nettyproxy.adapter.http.simpleserver.NettySimpleServerHandler;
import org.hum.nettyproxy.common.Constant;
import org.hum.nettyproxy.common.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ByteBufWebHelper {

	private static final Logger logger = LoggerFactory.getLogger(ByteBufWebHelper.class);
	private static final byte RETURN_LINE = 10;
	private static final byte[] _2_ReturnLine = (Constant.RETURN_LINE + Constant.RETURN_LINE).getBytes();

	public static String readLine(ByteBuf byteBuf) {
		StringBuilder sbuilder = new StringBuilder();

		byte b = -1;
		while (byteBuf.isReadable() && (b = byteBuf.readByte()) != RETURN_LINE) {
			sbuilder.append((char) b);
		}

		return sbuilder.toString().trim();
	}

	private static String WEB_ROOT;
	private static ByteBuf _404ByteBuf;
	private static ByteBuf _500ByteBuf;

	static {
		try {
			WEB_ROOT = NettySimpleServerHandler.class.getClassLoader().getResource("").toURI().getPath();
			WEB_ROOT += "webapps";

			_404ByteBuf = ByteBufWebHelper.readFile(Unpooled.directBuffer(), new File(WEB_ROOT + "/404.html"));
			_500ByteBuf = ByteBufWebHelper.readFile(Unpooled.directBuffer(), new File(WEB_ROOT + "/500.html"));
		} catch (Exception e) {
			WEB_ROOT = "";
			logger.error("init netty-simple-http-server error, can't init web-root-path", e);
		}
	}
	
	public static String getWebRoot() {
		return WEB_ROOT;
	}
	
	public static ByteBuf _404ByteBuf() {
		return _404ByteBuf;
	}
	
	public static ByteBuf _500ByteBuf() {
		return _500ByteBuf;
	}

	public static ByteBuf readFileFromWebapps(ByteBuf byteBuf, String filePath) throws IOException {

		return readFile(byteBuf, new File(WEB_ROOT + "/" + filePath));
	}
	
	public static ByteBuf readFile(ByteBuf byteBuf, File file) throws IOException {
		BufferedInputStream fileInputStream = null;
		try {
			fileInputStream = new BufferedInputStream(new FileInputStream(file));
			int read = -1;
			while ((read = fileInputStream.read()) != -1) {
				byteBuf.writeByte((byte) read);
			}
			return byteBuf;
		} finally {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
		}
	}

	public static String readFile2String(File file) throws FileNotFoundException, IOException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			String line = null;
			StringBuilder sbuilder = new StringBuilder();
			while ((line = br.readLine()) != null) {
				sbuilder.append(line);
			}
			return sbuilder.toString();
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}

    /**
     * ByteBuf -> HttpRequest
     * @param byteBuf
     * @return
     */
    public static HttpRequest decode(ByteBuf byteBuf) {
    	HttpRequest request = new HttpRequest();
    	// read request-line
    	request.setLine(ByteBufWebHelper.readLine(byteBuf));
    	
    	// parse to method
    	request.setMethod(request.getLine().split(" ")[0]);
    	
    	// read request-header
    	String line = null;
    	while (!(line = ByteBufWebHelper.readLine(byteBuf)).equals("")) {
    		int splitIndex = line.indexOf(":");
    		
    		if (splitIndex <= 0) {
    			continue;
    		}
    		
    		String key = line.substring(0, splitIndex).trim();
    		String value = line.substring(splitIndex + 1, line.length()).trim();
    		request.getHeaders().put(key, value);
    		
    		// parse to host and port
    		if (Constant.HTTP_HOST_HEADER.equalsIgnoreCase(key)) {
    			if (value.contains(":")) {
    				String[] arr = value.split(":");
	    			request.setHost(arr[0]);
	    			request.setPort(Integer.parseInt(arr[1]));
    			} else {
					request.setHost(value);
					request.setPort(Constant.HTTPS_METHOD.equalsIgnoreCase(request.getMethod()) ? Constant.DEFAULT_HTTPS_PORT : Constant.DEFAULT_HTTP_PORT);
    			}
    		}
    	}
    	
    	// read request-body
    	StringBuilder body = new StringBuilder();
    	while (!(line = ByteBufWebHelper.readLine(byteBuf)).equals("")) {
    		body.append(line);
    	}
    	request.setBody(body.toString());
    	
    	// reference ByteBuf
    	request.setByteBuf(byteBuf);
    	
    	// fixbug: 有些服务器要求比较严格，目前看多几换行比少换行更能有效的访问到正确的URL，如果删除这段\r\n\r\n代码，再访问这个URL就会没有响应：http://pos.baidu.com/auto_dup?psi=2825367ec24f0f2315cbfc2e69f5a2c0&di=0&dri=0&dis=0&dai=0&ps=0&enu=encoding&dcb=___baidu_union_callback_&dtm=AUTO_JSONP&dvi=0.0&dci=-1&dpt=none&tsr=0&tpr=1560287949982&ti=%E8%BF%993%E4%B8%AA%E6%9C%89%E5%85%B3%E6%8A%A4%E8%82%A4%E7%9A%84%E5%B0%8F%E7%AA%8D%E9%97%A8%EF%BC%8C%E7%94%A8%E8%BF%87%E4%BC%9A%E7%AB%8B%E9%A9%AC%E6%8F%90%E5%8D%87%E9%A2%9C%E5%80%BC%E5%93%A6%EF%BC%8C%E4%BD%A0%E7%9F%A5%E9%81%93%E4%BA%86%E5%90%97&ari=2&dbv=0&drs=3&pcs=1680x439&pss=1680x3139&cfv=0&cpl=0&chi=1&cce=true&cec=UTF-8&tlm=1560259149&rw=439&ltu=http%3A%2F%2Fbaijiahao.baidu.com%2Fs%3Fid%3D1636038282713233707&ltr=http%3A%2F%2Fnews.baidu.com%2F&ecd=1&uc=1680x961&pis=-1x-1&sr=1680x1050&tcn=1560287950&dc=4
    	byteBuf.writeBytes(_2_ReturnLine);
    	// reset bytebuf read_size, ensure readable
    	byteBuf.resetReaderIndex();
    	
    	return request;
    }
}
