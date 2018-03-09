package kontroler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class SieciLicznikowKontrolerUDP {

	private static String ipAgenta;
	private static int portAgenta = 2000;
	private static String komenda;
	private static String wartoscDoOperacji;
	private static int nowaWartosc;
	private static int iloscRetransmisji = 0;
	
	public static void sendMsgToAgent() {
		try(DatagramSocket dso = new DatagramSocket(portAgenta);) {
			dso.setSoTimeout(3000); //3 sek
			byte[] bufforOut = new byte[256];
			String msg = komenda + "_" + wartoscDoOperacji;		//tworzymy dane do wysłania
			if(komenda.equals("set")) {		//w set przesyłamy jeszcze nową wartość
				String tmp = "_" + nowaWartosc;
				msg += tmp;
			}
			bufforOut = msg.getBytes();
			DatagramPacket dpS = new DatagramPacket(bufforOut, bufforOut.length, InetAddress.getByName(ipAgenta), portAgenta);
			dso.send(dpS);	//wysyłamy
			byte[] bufforIn = new byte[256];
			DatagramPacket dpR = new DatagramPacket(bufforIn, bufforIn.length);
			dso.receive(dpR);		//tu może być timeout jak odp nie dojdzie, wtedy robimy retransmisje
			String answer = new String(dpR.getData());
			answer = answer.trim();		//usunięcie nadmiarowych spacji
			answer = answer.substring(0, answer.length());	//usunięcie jeszcze jednej pozostałej spacji
			if(komenda.equals("get")) {		//sprawdzenie co dostaliśmy w odpowiedzi i jaki komunikat wypisać na konsole w zależności od komendy
				System.out.println(wartoscDoOperacji + ": " + answer);
			} else if(answer.equals("OK")) {
				System.out.println("Operacja " + komenda + " na " + wartoscDoOperacji + " udała się");
			} else {
				System.out.println("Operacja " + komenda + " na " + wartoscDoOperacji + " nie powiodła się");
			}
		} catch (SocketTimeoutException e) {
			if(iloscRetransmisji != 2) {	// jeśli iloscRetransmisji = 2 to znaczy, że były już 3 próby zrealizowania zadania kontrolera
				iloscRetransmisji++;
				sendMsgToAgent();	//ponawiamy wysyłkę wiadomości do agenta
			} else {
				System.out.println("Nie udało się uzyskać odpowiedzi od hosta.");
			}	

		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		if(args.length != 3 && args.length != 4) {
			System.err.println("usage: java SieciLicznikowKontrolerUDP <IP agenta> <komenda> <wartosc na ktorej ma byc wykonana operacja> <nowa liczba do wstawienia - potrzebne tylko gdy komenda to set>");
			System.exit(1);
		}
		// sprawdzenie poprawności parametrów
		if(!args[0].matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {  //zle ip, dajemy error
			System.err.println("Niepoprawne IP agenta");
			System.exit(2);
		}
		
		if(!args[1].equals("get") && !args[1].equals("set")) {
			System.err.println("Poprawna nazwa komendy to get lub set");
			System.exit(3);
		}
		
		if(!args[2].equals("period") && !args[2].equals("counter")) {
			System.err.println("Poprawna nazwa wartości na której będzie wykonana operacja to period lub counter");
			System.exit(4);
		}
		//dodatkowe sprawdzenie parametru, tylko dla set
		if(args.length == 4) {
			try {
				nowaWartosc = Integer.parseInt(args[3]);
			} catch (NumberFormatException e) {
				System.err.println("Niepoprawny format nowej wartości do ustawienia, proszę podać liczbę całkowitą nieujemną");
				System.exit(5);
			}
			if(nowaWartosc == 0 && wartoscDoOperacji.equals("period")) {
				System.err.println("Liczba zero jest niedozwolona jako kwant czasu");
				System.exit(6);
			}
			if(nowaWartosc < 0) {
				System.err.println("Niepoprawny format nowej wartości do ustawienia, proszę podać liczbę całkowitą nieujemną");
				System.exit(7);
			}
		}
		ipAgenta = args[0];
		komenda = args[1];
		wartoscDoOperacji = args[2];
		
		sendMsgToAgent();
	}

}
