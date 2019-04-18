// Author: Jacob Wilkins

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

// Each Client Connection will be managed in a dedicated thread because it implements Runnable
public class HTTPServer implements Runnable {
	static final File ROOT = new File("."); // Abstract pathname of a root file
	static final int PORT = 6789; // Port to listen connection
	static final String movedFiles[] = {"/old_page.html", "/new_page.html", "/old_page2.html", "/new_page2.html"}; // List of moved files with their new file name succeeding them
	
	private Socket connect;	// Client Connection via Socket Class
	
	// Set the socket for each new instance of HTTPServer
	public HTTPServer(Socket c) {
		connect = c;
	}
	
	public static void main(String[] args) {
		try {
			ServerSocket serverConnect = new ServerSocket(PORT); // Create server socket bounded to port 6789
			System.out.println("Server started.");
			System.out.println("Listening for connections on port : " + PORT + " ...\n");
			
			while(true) { // Listen until user halts server execution
				HTTPServer myServer = new HTTPServer(serverConnect.accept());	// Create HTTPServer object using server socket
				
				System.out.println("Connection opened. (" + new Date() + ")");
				
				Thread thread = new Thread(myServer); // Create dedicated thread to manage the client connection
				thread.start(); // Begin execution of the thread
			}
			
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}
	
	// Used to perform actions for the thread (required because the HTTPServer class implements Runnable)
	@Override
	public void run() {
		// Manage our particular client connection
		BufferedReader in = null; // Read text in from the character input stream and buffers the input from the specified file
		PrintWriter out = null; // Print test to the character output stream
		BufferedOutputStream dataOut = null;
		String fileRequested = null;
		
		try {
			in = new BufferedReader(new InputStreamReader(connect.getInputStream()));	// Read characters from the client via input stream on the socket
			out = new PrintWriter(connect.getOutputStream());	// Get character output stream to client (for header)
			dataOut = new BufferedOutputStream(connect.getOutputStream());	// Get binary output stream to client (for requested data)
			
			String input = in.readLine();	// Get first line of the request from the client
			if (input == null) {
				input = "No input received";
			}
			StringTokenizer parse = new StringTokenizer(input);	// Parse the request with a string tokenizer
			String method = parse.nextToken().toUpperCase(); // Get the HTTP method of the client
			fileRequested = parse.nextToken().toLowerCase();	// Get file requested

			if (hasMoved(fileRequested) != -1) {	// Determines if file has been moved
				movedPermanently(out, dataOut, fileRequested); //Create HTTP response for moved permanently
			} else {
				// GET method
				if (fileRequested.endsWith("/")) {	// If file ends with "/", make it "/index.html"
					fileRequested += "index.html";
				}
				ok(out, dataOut, fileRequested, method); // Create HTTP response for ok
			}
		} catch (FileNotFoundException fnfe) { // Exception if file is not found
			try {
				fileNotFound(out, dataOut, fileRequested); // Create HTTP response for file not found
			} catch (IOException ioe) {
				System.err.println("Error with file not found exception : " + ioe.getMessage());
			}
		} catch (IOException ioe) {
			System.err.println("Server error : " + ioe);
		} finally {
			try {
				in.close();	// Close character input stream
				out.close(); // Close print writer
				dataOut.close(); // Close buffered output stream
				connect.close();	// Close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream: " + e.getMessage());
			}
			
			System.out.println("Connection closed.");
		}
	}
	
	private void movedPermanently(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
		File file = new File(ROOT, movedFiles[hasMoved(fileRequested)]); // Create a file object with the new file name
		int fileLength = (int) file.length();	// Get length of file and cast from long to int
		String content = getContentType(movedFiles[hasMoved(fileRequested)]);	// Determine the type of content using the getContentType(String fileRequested) function
				
		byte[] fileData = readFileData(file, fileLength);	// Read file data into a byte array that will be written to the buffered output stream
				
		// Send HTTP Headers
		out.println("HTTP/1.1 301 Moved Permanently");
		out.println("Server: HTTP Server from Jacob Wilkins : 1.0");
		out.println("Date: " + new Date()); // Date() gets the current time and date
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println();
		out.flush(); // Flush character output stream buffer
		dataOut.write(fileData, 0, fileLength);	// Write file data to the buffered output stream
		dataOut.flush(); // Flush the buffered output stream
				
		System.out.println("\nFile " + movedFiles[hasMoved(fileRequested)] + " of type " + content + " returned");
	}
	
	private void ok(PrintWriter out, OutputStream dataOut, String fileRequested, String method) throws IOException {
		File file = new File(ROOT, fileRequested);	// Create a file object with the requested file name
		int fileLength = (int) file.length();	// Get length of file and cast from long to int
		String content = getContentType(fileRequested);	// Get content type of the requested file
				
		if (method.equals("GET")) { // GET method so we return content
					byte[] fileData = readFileData(file, fileLength);	// Read file data into a byte array that will be written to the buffered output stream
					
			// Send HTTP Headers
			out.println("HTTP/1.1 200 OK");
			out.println("Server: HTTP Server from Jacob Wilkins : 1.0");
			out.println("Date: " + new Date()); // Date() gets the current time and date
			out.println("Content-type: " + content);
			out.println("Content-length: " + fileLength);
			out.println();	// Blank line between headers and content
			out.flush();	// Flush character output stream buffer
			dataOut.write(fileData, 0, fileLength); // Write file data to the buffered output stream
			dataOut.flush(); // Flush the buffered output stream
		}
				
		System.out.println("\nFile " + fileRequested + " of type " + content + " returned");
	}
	
	// Creates an HTTP response to indicate the file is not found
	private void fileNotFound(PrintWriter out, OutputStream dataOut, String fileRequested) throws IOException {
		File file = new File(ROOT, "404.html"); // Create a file object with name of the 404 file
		int fileLength = (int) file.length(); // Get length of file and cast from long to int
		String content = "text/html"; // Set content type to that of the 404 file
		byte[] fileData = readFileData(file, fileLength); // Read file data into a byte array that will be written to the buffered output stream
		
		out.println("HTTP/1.1 404 File Not Found");
		out.println("Server: HTTP Server from Jacob Wilkins : 1.0");
		out.println("Date: " + new Date()); // Date() gets the current time and date
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println();
		out.flush(); // Flush character output stream buffer
		dataOut.write(fileData, 0, fileLength); // Write file data to the buffered output stream
		dataOut.flush(); // Flush the buffered output stream
		
		System.out.println("File " + fileRequested + " not found");
	}
	
	// Determine if a file has been moved
	static int hasMoved(String fileRequested) { // Returns the index of the new file or -1 otherwise
		int i = 0;
		// Go through the moved files array to determine if the requested file is there
		for (i = 0; i < movedFiles.length; i=i+2) { // Old and new files are paired in 2's
			if (movedFiles[i].equals(fileRequested)) { 
				return i + 1; // The new file location will always be one ahead of the old file
			}
		}
		return -1; // File has not been moved
	}
	
	// Read file data into a byte array
	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null; // File input stream initialized to null
		byte[] fileData = new byte[fileLength]; // File will be read into byte array
		
		try {
			fileIn = new FileInputStream(file); // Create file input stream by opening a connection to the file
			fileIn.read(fileData); // Read input from the file
		} finally {
			if (fileIn != null) {
				fileIn.close(); // Close the file input stream
			}
		}
		return fileData;
	}
	
	// Return supported content types
	private String getContentType(String fileRequested) { // End of the file name is tested to determine which type to return
		if (fileRequested.endsWith(".htm") || fileRequested.endsWith(".html")) {
			return "text/html";
		} else if (fileRequested.endsWith(".png")) {
			return "text/png";
		} else if (fileRequested.endsWith(".jpg")) {
			return "text/jpg";
		} else {
			return "text/plain";
		}
	}
	
}
