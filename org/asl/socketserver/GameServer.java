package org.asl.socketserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/***
 * Accepts incoming network connection requests and dispatches them onto
 * parallel game threads. The port number to monitor and the maximum number of
 * simultaneous threads are both configurable as command line arguments.
 * 
 * @author K Collins
 * @version Fall, 2017
 */
public class GameServer {

	final static Logger LOGGER; // destination for error messages
	final static int DEFAULT_PORT_NUM = 0; // if not specified, choose random
	final static int DEFAULT_MAX_USERS = 10; // if not specified, allow 10
	final static String LOG_FILE = "server_output.log";

	static {
		LOGGER = Logger.getLogger(GameServer.class.getName());
	}

	/**
	 * Opens a server port to listen for incoming network requests. When a request
	 * is received, accepts the connection, wraps the resulting socket in a thread,
	 * and starts the thread process.
	 * 
	 * @param args[0]
	 *            the port to use when listening for connections; if no command line
	 *            arguments are provided, uses the default
	 * @param args[1]
	 *            the max number of concurrent connections to accept; if no command
	 *            line arguments, uses the default
	 * @throws java.io.IOException
	 *             if unable to read from socket
	 */
	public static void main(String[] args) throws IOException {
		int desiredPort = DEFAULT_PORT_NUM;
		int maxConnections = DEFAULT_MAX_USERS;
		boolean createLogFile = false;
		try {
			desiredPort = Integer.parseInt(args[0]);
			maxConnections = Integer.parseInt(args[1]);
			createLogFile = Boolean.parseBoolean(args[2]);
		} catch (ArrayIndexOutOfBoundsException
				| NumberFormatException e) {
			LOGGER.info(
					"Command line arguments missing or faulty.  Launching with program defaults.");
		}
		if (createLogFile)
			attachLogFileHandler();
		String refuseNewConnectionMessage = "The server limit of "
				+ maxConnections
				+ ((maxConnections == 1) ? " connection"
						: " connections")
				+ " has been reached.  Please try again, later.";

		try (ServerSocket socketRequestListener = new ServerSocket(
				desiredPort)) {
			LOGGER.info("SERVER: GameServer started on port: "
					+ socketRequestListener.getLocalPort()
					+ ".  Thread capacity: " + maxConnections);
			GameTracker.initialize();
			while (true) {
				// the following call blocks until a connection is made
				Socket socket = socketRequestListener.accept();
				InetAddress remoteMachine = socket.getInetAddress();
				// String remoteHost = remoteMachine.getHostName();

				// LOGGER.info("Incoming connection request from " + remoteMachine);

				int numActiveSockets = Thread.activeCount() - 1;
				if (numActiveSockets < maxConnections) {
					new Thread(new GameThread(socket)).start();
					numActiveSockets++;
					LOGGER.info("HELLO: " + remoteMachine
							+ " Number of current connections: "
							+ numActiveSockets);
				} else {
					PrintWriter out = new PrintWriter(
							socket.getOutputStream());
					out.println(refuseNewConnectionMessage);
					out.close();
					socket.close();
					LOGGER.warning("SORRY: " + remoteMachine
							+ ".  Number of current connections: "
							+ numActiveSockets);
				}
			}
		}
	}

	private static void attachLogFileHandler() {
		// String datedFile = LocalDateTime.now().toString()+".log";
		try {
			FileHandler fh = new FileHandler(LOG_FILE);
			LOGGER.addHandler(fh);
			fh.setFormatter(new ServerLogFormatter());
		} catch (SecurityException e) {
			LOGGER.warning(e.getMessage());
		} catch (IOException e) {
			LOGGER.warning(e.getMessage());
		}
	}

	static class ServerLogFormatter extends SimpleFormatter {
		@Override
		public String format(LogRecord log) {
			String s = log.getMessage();
			boolean isCoded = true;
			int colon = s.indexOf(":");
			if (colon >= 0) {
				String preamble = s.substring(0, colon);
				for (char c : preamble.toCharArray()) {
					if (Character.isLowerCase(c))
						isCoded = false;
				}
			}
			if (isCoded || s.contains("<--") || s.contains("-->")) {
				// strip and remember opcode
				String opcode = s.substring(0, s.indexOf(" "));
				// collect from ip address onwards
				s = s.substring(s.indexOf("/") + 1);
				// strip and remember ip address
				String ip = s.substring(0, s.indexOf(" "));
				// collect remainder of message
				s = s.substring(s.indexOf(" ") + 1).trim();
				if (opcode.equals("-->") || opcode.equals("<--")) {
					s = String.format("%-15s %10s %s", ip, opcode,
							s);
				} else
					s = String.format("%-15s %10s %s", ip,
							opcode, s);
			}
			return String.format("%.80s%n", s);
		}

	}

}
