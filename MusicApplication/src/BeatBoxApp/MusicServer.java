package BeatBoxApp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MusicServer {

	List<ObjectOutputStream> clientOutputStreams;

	public static void main(String[] args) {
		new MusicServer().go();
	}

	public void go() {
		clientOutputStreams = new ArrayList();

		try(ServerSocket serverSocket = new ServerSocket(4242)){ 

			while (true) {
				Socket clientSocket = serverSocket.accept();
				ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
				clientOutputStreams.add(out);

				Thread t = new Thread(new ClientHandler(clientSocket));
				t.start();
				
				System.out.println("got a connection");
			}
		}
					
			catch (IOException e) {
				System.out.println("In go method");
			e.printStackTrace();
		}
	
		
	}

	protected void tellEveryone(Object one, Object two) {
		Iterator it = clientOutputStreams.iterator();

		while (it.hasNext()) {
			try {
				ObjectOutputStream out = (ObjectOutputStream) it.next();
				out.writeObject(one);
				out.writeObject(two);
			} catch (Exception ex) {
				System.out.println("In tellEveryone method");
				ex.getMessage();
			}
		}
	}
	
	
	public class ClientHandler implements Runnable{
		ObjectInputStream in;
		Socket sock;

		public ClientHandler(Socket clientSsocket) {
			try {
				sock = clientSsocket;
				in = new ObjectInputStream(sock.getInputStream());

			} catch (Exception ex) {
				System.out.println("In ClientHandler class");

				ex.printStackTrace();
			}
		}

		@Override
		public void run() {
			Object o2 = null;
			Object o1 = null;
			try {
				while ((o1 = in.readObject()) != null) {
					o2 = in.readObject();
					System.out.println("read two object");
					tellEveryone(o1, o2);
				}
			} catch (Exception ex) {
				
				System.out.println("In run method");
				ex.printStackTrace();
			}

		}

	};
		
}
