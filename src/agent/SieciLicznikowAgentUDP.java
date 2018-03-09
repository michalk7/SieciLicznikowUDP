package agent;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SieciLicznikowAgentUDP {

	private static AtomicLong time = new AtomicLong();		//licznik
	private static AtomicInteger kwantCzasu = new AtomicInteger(5);		//domyślnie kwant wynosi 5
	private static int port = 2000;		//port, dla każdego agenta taki sam
	private static InetAddress broadcast;		
	private static InetAddress myAddress;	//ip Agenta
	private static boolean synSend = false;
	private static List<Long> czasyOdAgentow = new ArrayList<>();
	private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static ScheduledFuture<?> synTask;
	
	public static void main(String[] args) {
		//sprawdzenie poprawnosci parametrów
		if(args.length != 2) {
			System.err.println("usage: java SieciLicznikowAgentUDP <wartosc licznika> <kwant czasu na SYN>");
			System.exit(1);
		}
		
		try {
			time.set(Long.parseLong(args[0]));
		} catch ( Exception e) {
			System.err.println("Proszę podać wartość licznika w prawidłowej formie");
			System.exit(2);
		}
		
		try {
			kwantCzasu.set(Integer.parseInt(args[1]));
		} catch( Exception e) {
			System.err.println("Proszę podać kwant czasu w prawidłowej formie");
			System.exit(3);
		}
		
		System.out.println("By zakończyć program proszę wpisać exit");
		
		//tu startujemy z liczeniem czasu
		System.out.println("Time na wejsciu " + time);
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				time.incrementAndGet();
			}
			
		}, 0, 1);
		
		//ten fragment kodu przeszukuje interfejsy sieciowe komputera i znajduje nam OSTATNI działający interfejs sieciowy,
		// tzn. w sytuacji, gdy mamy 2 działające karty sieciowe to zostanie wybrana druga z nich
		// Podczas wyszukiwania odrzucam wyłączone interfejsy, wirtualne, adaptery i interjefsy programu VMware, które sprawiały problemy podczas wykonywania projektu
		try {
			Enumeration<NetworkInterface> nInterfaces = NetworkInterface.getNetworkInterfaces();
			while (nInterfaces.hasMoreElements()) {
				NetworkInterface ni = nInterfaces.nextElement();
				if(ni.isUp() && !ni.isVirtual() && ni.getHardwareAddress() != null) {
					String name = ni.getDisplayName();		//metoda zwraca nazwę urządzenia sieciowego
					Pattern p = Pattern.compile("(Adapter)|(Virtual)|(VMware)", Pattern.CASE_INSENSITIVE);
					Matcher m = p.matcher(name);
					if(!m.find()) {
						Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
						while(inetAddresses.hasMoreElements()) {
							InetAddress address = inetAddresses.nextElement();
							if(address.isSiteLocalAddress() && !address.isAnyLocalAddress()) {
								myAddress = address;
							}
						}
					}
				}
			}
			
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		
		
		
		try {		//tutaj wyciągam informacje o adresie broadcast na podstawie IP agenta
			InetAddress addr;
			if(myAddress != null) {		//w razie gdyby znajdywanie IP po interfejsach sieciowych zawiodło, to przypisuje to co zwraca getLocalHost
				addr = myAddress;
			} else {
				addr = InetAddress.getLocalHost();
			}
			NetworkInterface ni = NetworkInterface.getByInetAddress(addr);
			List<InterfaceAddress> list = ni.getInterfaceAddresses();
			for(InterfaceAddress ia : list) {
				if(ia.getAddress().equals(addr)) {
					broadcast = ia.getBroadcast();
					System.out.println("Broadcast " + broadcast.getHostAddress());
					//wyświetlam adres broadcast, żeby użytkownik wiedział czy program się nie pomylił i określił prawidłowo adres
				}
			}
		
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(4);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(5);
		}
		
		//poniższy kod realizuje synchronizacje czasu, wysyła pakiet z zawartością "CLK" na broadcast 
		//i czeka aż inny wątek zbierze odpowiedzi, pózniej liczy nową wartość time i ustawia ją.
		Runnable syn = () -> {
			byte[] buf = "CLK".getBytes();
			DatagramPacket dp = new DatagramPacket(buf, buf.length, broadcast, port);
			try (DatagramSocket sender = new DatagramSocket();) {
				czasyOdAgentow.clear(); // dla pewności czyszczę listę czasów
				sender.send(dp);
				synSend = true;
				
				Thread.sleep((kwantCzasu.get()*1000) / 2);		//czeka połowe kwantu czasu na odpowiedzi od agentow
				
				long suma = 0L;
				for (long l : czasyOdAgentow) {
					suma += l;
				}
				suma += time.get();		//dodajemy swój czas do listy
				time.set(suma / (czasyOdAgentow.size() + 1));
				czasyOdAgentow.clear();
				System.out.println("Czas " + time);
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				
			}
		};
		
		//kod metody run interfejsu Runnable, czeka na przychodzące pakiety i w zależności od odebranych danych podejmuje odpowiednie działania
		Runnable listenForDatagrams = () -> {
			
			try (DatagramSocket dso = new DatagramSocket(port);) {
				if(!dso.getBroadcast()) {
					System.out.println("Setting broadcast");
					dso.setBroadcast(true);
				}
				dso.setSoTimeout((kwantCzasu.get()*1000) / 2); //timeout ustawiany na połowę kwantu
				while(true) {
					if(Thread.currentThread().isInterrupted()) {	//obsługa zamknięcia wątku
						return;
					}
					try {
						byte[] buffor = new byte[512];
						DatagramPacket dp = new DatagramPacket(buffor, buffor.length);
						dso.receive(dp);
						String data = new String(dp.getData());
						data = data.trim();		//obcinam zbędne spacje
						data = data.substring(0, data.length());	//została jedna spacja, też obcinam
						
						if(synSend && !dp.getAddress().equals(myAddress)) {		
							//jeśli wysłano CLK na broadcast i wiadomość nie jest z ip naszego agenta 
							//(agent może wysłać czas sam do siebie, a nawet tak robi) to dodajemy czas do listy, 
							//jeśli oczywiście zawartość pakietu to czas
							try {
								long czas = Long.parseLong(data);
								czasyOdAgentow.add(czas);
							} catch (NumberFormatException e) {
								//po prostu nie dodajemy tego czegos, napewno nie jest to czas
							}
						}
						if(data.equals("CLK")) {	//jak CLK to odsyłamy czas
							byte[] buf = Long.toString(time.get()).getBytes();
							DatagramPacket packetToSend = new DatagramPacket(buf, buf.length, dp.getAddress(), port);
							DatagramSocket dsSender = new DatagramSocket();
							dsSender.send(packetToSend);
							dsSender.close();
						}
						
						//komunikaty get i set przychodzą jako słowa/liczby oddzielone znakiem _ stąd metoda contains
						if(data.contains("get")) {	//przy get odsyłamy odpowiednie dane
							String[] tmp = data.split("_");
							byte[] b = new byte[256];
							if(tmp[1].equals("counter")) {
								b = Long.toString(time.get()).getBytes();
							} else if(tmp[1].equals("period")) {
								b = Integer.toString(kwantCzasu.get()).getBytes();
							}
							DatagramPacket answer = new DatagramPacket(b, b.length, dp.getAddress(), port);
							DatagramSocket dsSender = new DatagramSocket();
							dsSender.send(answer);
							dsSender.close();
						}
						if(data.contains("set")) {		//przy set zmieniamy wartość i odsyłamy odpowiedz czy operacja się udała
							String[] tmp = data.split("_");
							boolean isOk = false;
							if(tmp[1].equals("counter")) {
								try {
									time.set(Long.parseLong(tmp[2]));
									isOk = true;
								} catch(NumberFormatException e) {
									
								}
							} else if(tmp[1].equals("period")) {
								try {
									kwantCzasu.set(Integer.parseInt(tmp[2]));
									dso.setSoTimeout((kwantCzasu.get()*1000) / 2);		//timeout z nowym kwantem
									synTask.cancel(true);	//anulujemy synTask ze starym kwantem i odpalamy z nowym
									synTask = scheduler.scheduleAtFixedRate(syn, 0, kwantCzasu.get(), TimeUnit.SECONDS);
									isOk = true;
								} catch(NumberFormatException e) {
									
								}
							}
							byte[] b = new byte[128];
							if(isOk) {
								b = "OK".getBytes();
							} else {
								b = "Ups".getBytes();
							}
							DatagramPacket answer = new DatagramPacket(b, b.length, dp.getAddress(), port);
							DatagramSocket dsSender = new DatagramSocket();
							dsSender.send(answer);
							dsSender.close();
						}
					} catch (SocketTimeoutException excTimeout) {
						//tutaj obsluga timeoutu
						if(synSend) {	//czas minął, zmieniamy warunek, nie zbieramy już czasów dla tego wykonania operacji synchronizacji
							synSend = false;
						}
					}
				}
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
		
		//wątek nasłuchujacy
		Thread listenThread = new Thread(listenForDatagrams);
		listenThread.start();
		
		
		//uruchomienie zadania synchronizacji czasu
		synTask = scheduler.scheduleAtFixedRate(syn, 0, kwantCzasu.get(), TimeUnit.SECONDS);
	
		//obsluga zakonczenia programu
		Scanner scan = new Scanner(System.in);
		while(scan.hasNext()) {
			String str = scan.next();
			if(str.equalsIgnoreCase("exit")) {
				System.out.println("Program zostanie wyłączony, do zobaczenia!");
				timer.cancel();
				synTask.cancel(true);
				scheduler.shutdown();
				listenThread.interrupt();
				break;
			}
		}
		scan.close();
	}
}
