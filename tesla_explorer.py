#!/Users/sebastian.wien/private/ev-monitor/venv/bin/python3
"""
Tesla API Explorer for EV Monitor Integration
Holt alle relevanten Daten für Charging-Log Import
"""

import teslapy
import json
from datetime import datetime
from pathlib import Path

def anonymize_sensitive_data(data):
    """Optional: Sensible Daten anonymisieren"""
    if isinstance(data, dict):
        # VIN anonymisieren (letzten 4 Stellen behalten)
        if 'vin' in data:
            data['vin'] = 'XXX' + data['vin'][-4:] if data['vin'] else None

        # GPS-Koordinaten auf 2 Dezimalstellen (grobe Location)
        if 'latitude' in data:
            data['latitude'] = round(data['latitude'], 2) if data['latitude'] else None
        if 'longitude' in data:
            data['longitude'] = round(data['longitude'], 2) if data['longitude'] else None

        # Rekursiv durch alle nested dicts
        for key, value in data.items():
            if isinstance(value, dict):
                anonymize_sensitive_data(value)
            elif isinstance(value, list):
                for item in value:
                    if isinstance(item, dict):
                        anonymize_sensitive_data(item)

    return data

def explore_tesla_api(email, anonymize=True):
    """
    Verbindet mit Tesla API und holt alle relevanten Daten
    """
    print("🔐 Verbinde mit Tesla API...")
    tesla = teslapy.Tesla(email)

    # Login (öffnet Browser für OAuth)
    print("👉 Browser öffnet sich gleich für Login...")
    tesla.fetch_token()
    print("✅ Login erfolgreich!\n")

    # Fahrzeuge holen
    print("🚗 Hole Fahrzeug-Liste...")
    vehicles = tesla.vehicle_list()
    print(f"✅ {len(vehicles)} Fahrzeug(e) gefunden\n")

    all_data = {
        'timestamp': datetime.now().isoformat(),
        'vehicles': []
    }

    for idx, car in enumerate(vehicles):
        print(f"--- Fahrzeug {idx + 1} ---")
        print(f"Display Name: {car['display_name']}")
        print(f"Model: {car.get('vehicle_config', {}).get('car_type', 'Unknown')}")
        print(f"State: {car['state']}")

        vehicle_data = {
            'basic_info': {
                'id': car['id'],
                'vehicle_id': car['vehicle_id'],
                'display_name': car['display_name'],
                'state': car['state'],
                'vin': car['vin'],
            }
        }

        # Nur wenn Auto online/wach ist, detaillierte Daten holen
        if car['state'] != 'online':
            print("⚠️  Auto schläft - versuche aufzuwecken...")
            try:
                car.sync_wake_up(timeout=30)
                print("✅ Auto ist wach!")
            except Exception as e:
                print(f"❌ Konnte Auto nicht wecken: {e}")
                print("💡 Tipp: Öffne die Tesla App, dann probier's nochmal\n")
                vehicle_data['error'] = 'vehicle_asleep'
                all_data['vehicles'].append(vehicle_data)
                continue

        print("\n📊 Hole detaillierte Daten...")

        try:
            # Haupt-Daten
            vehicle_details = car.get_vehicle_data()

            # Relevante Sections extrahieren
            vehicle_data.update({
                'charge_state': vehicle_details.get('charge_state', {}),
                'drive_state': vehicle_details.get('drive_state', {}),
                'vehicle_state': vehicle_details.get('vehicle_state', {}),
                'climate_state': vehicle_details.get('climate_state', {}),
                'vehicle_config': vehicle_details.get('vehicle_config', {}),
            })

            # Charging-relevante Infos highlighten
            charge = vehicle_data['charge_state']
            print(f"\n⚡ Lade-Status:")
            print(f"  Battery Level: {charge.get('battery_level')}%")
            print(f"  Battery Range: {charge.get('battery_range')} miles")
            print(f"  Charge Energy Added: {charge.get('charge_energy_added')} kWh")
            print(f"  Charging State: {charge.get('charging_state')}")
            print(f"  Charger Power: {charge.get('charger_power')} kW")

            # Location
            drive = vehicle_data['drive_state']
            print(f"\n📍 Location:")
            print(f"  Latitude: {drive.get('latitude')}")
            print(f"  Longitude: {drive.get('longitude')}")

            # Battery Config
            config = vehicle_data['vehicle_config']
            print(f"\n🔋 Batterie-Specs:")
            print(f"  Battery: {config.get('battery_type', 'Unknown')}")
            print(f"  Trim: {config.get('trim_badging', 'Unknown')}")

            print("✅ Daten erfolgreich abgerufen!")

        except Exception as e:
            print(f"❌ Fehler beim Abrufen: {e}")
            vehicle_data['error'] = str(e)

        all_data['vehicles'].append(vehicle_data)
        print()

    # Optional anonymisieren
    if anonymize:
        print("🔒 Anonymisiere sensible Daten (VIN, GPS auf 2 Dezimalstellen)...")
        all_data = anonymize_sensitive_data(all_data)

    # In Datei speichern
    filename = f"tesla_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    filepath = Path.home() / filename

    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(all_data, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Daten gespeichert in: {filepath}")
    print(f"📦 Dateigröße: {filepath.stat().st_size / 1024:.1f} KB")

    # Access Token für EV Monitor Integration ausgeben
    print("\n" + "=" * 60)
    print("🔑 WICHTIG: Für EV Monitor Integration brauchst du:")
    print("=" * 60)
    try:
        token = tesla.token
        access_token = token.get('access_token', 'N/A')
        expires_in = token.get('expires_in', 'N/A')

        print(f"\n📋 Access Token:")
        print(f"   {access_token[:50]}...{access_token[-20:] if len(access_token) > 70 else ''}")
        print(f"\n⏰ Token gültig für: {expires_in} Sekunden (~{expires_in/86400:.1f} Tage)")
        print(f"\n📝 Tesla ID (für API): {vehicles[0]['id'] if vehicles else 'N/A'}")
        print(f"   (Die lange Zahl - diese brauchst du für EV Monitor!)")

        # Token auch in separate Datei speichern (sicher)
        token_file = Path.home() / f"tesla_token_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        with open(token_file, 'w') as f:
            f.write(f"Access Token:\n{access_token}\n\n")
            f.write(f"Tesla ID (für EV Monitor API):\n{vehicles[0]['id'] if vehicles else 'N/A'}\n\n")
            f.write(f"Vehicle ID (legacy, nicht verwenden):\n{vehicles[0]['vehicle_id'] if vehicles else 'N/A'}\n\n")
            f.write(f"Expires in: {expires_in} seconds\n")
        print(f"\n💾 Token gespeichert in: {token_file}")
        print("\n⚠️  WICHTIG: Diese Datei enthält sensible Daten! Nicht teilen!")

    except Exception as e:
        print(f"\n❌ Konnte Token nicht extrahieren: {e}")

    print("\n👉 Nutze Access Token + Tesla ID im EV Monitor UI!")

    return filepath

if __name__ == "__main__":
    print("=" * 60)
    print("🚗 Tesla API Explorer für EV Monitor")
    print("=" * 60)
    print()

    email = input("Tesla Account Email: ").strip()

    print("\n🔒 Anonymisierung:")
    print("  - VIN wird gekürzt (nur letzte 4 Ziffern)")
    print("  - GPS auf ~1km genau (2 Dezimalstellen)")
    anonymize = input("Daten anonymisieren? (j/n) [j]: ").strip().lower() != 'n'

    print()

    try:
        filepath = explore_tesla_api(email, anonymize)
        print("\n🎉 Fertig! Viel Erfolg mit der Integration!")
    except KeyboardInterrupt:
        print("\n\n👋 Abgebrochen!")
    except Exception as e:
        print(f"\n❌ Fehler: {e}")
        import traceback
        traceback.print_exc()