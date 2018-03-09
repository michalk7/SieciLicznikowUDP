# SieciLicznikowUDP

SieciLicznikowUDP - program główny, umożliwiający odmierzanie upływu czasu na hostach w sieci lokalnej oraz automatycznie synchronizujący swój licznik co określony przez użytkownika czas. Program „Kontroler” umożliwia odczyt oraz zmianę czasu synchronizacji i aktualnego licznika dla wybranego hosta.

# Jak używać
 - Program główny (Agent) usage: java SieciLicznikowAgentUDP <wartosc licznika> <kwant czasu na SYN>
 - Kontroler - usage: java SieciLicznikowKontrolerUDP <IP agenta> <komenda> <wartosc na ktorej ma byc wykonana operacja> <nowa liczba do wstawienia - potrzebne tylko gdy komenda to set>
 - komendy: set, get
 - wartosci: counter, period

# Wymagania:
 - Java 8
