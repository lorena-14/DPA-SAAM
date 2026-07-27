#include <Wire.h>
#include "MAX30105.h"
#include "heartRate.h"

//#include <ArduinoJson.h> // transmision del mensaje en formato JSON para ser leido como JSONObject

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Pines
#define SDA_PIN 8 
#define SCL_PIN 9
#define BUTTON_PIN 6

#define debug Serial

MAX30105 particleSensor;

// Bluetooth LE 
#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;

//  Botón (antirrebote) 
bool lastReading = HIGH;       // última lectura cruda del pin
bool stableState = HIGH;       // estado confirmado tras el debounce
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 50;

// CALCULAR BPM 
const byte RATE_SIZE = 4;        // Tamaño del promedio móvil
byte rates[RATE_SIZE];           // Array de últimos BPM válidos
byte rateSpot = 0;
long lastBeat = 0;               // Tiempo (ms) del último latido detectado
float beatsPerMinute = 0;
int beatAvg = 0;

// Conexion BLE
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    deviceConnected = true;
    debug.println("Dispositivo BLE conectado");
  }
  void onDisconnect(BLEServer *pServer) {
    deviceConnected = false;
    debug.println("Dispositivo BLE desconectado");
    pServer->startAdvertising();
  }
};

void setup()
{
  debug.begin(115200);
  debug.println("Inicializando MAX30102...");

  pinMode(BUTTON_PIN, INPUT_PULLUP);

  Wire.begin(SDA_PIN, SCL_PIN);

  if (particleSensor.begin(Wire, I2C_SPEED_FAST) == false)
  {
    debug.println("MAX30102 no encontrado. Revisar conexiones.");
    while (1);
  }

  // Configuración explícita en vez de setup() por defecto
  byte ledBrightness = 0x1F; // Sube desde 0x0A
  byte sampleAverage = 8;
  byte ledMode = 2;          // Red + IR (no necesitas Green para BPM/SpO2)
  int sampleRate = 100;
  int pulseWidth = 411;
  int adcRange = 4096;
  
  particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange);
  //particleSensor.setPulseAmplitudeRed(0x0A);  //Amplitud del LED ROJO (Revisar esto, de momento valor por defecto 0x0A)
  particleSensor.setPulseAmplitudeGreen(0);

  //  Inicializar BLE 
  BLEDevice::init("ESP32_MAX30102");  // Nombre Servidor
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pTxCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  pServer->getAdvertising()->start();
  debug.println("BLE listo. Esperando conexión...");
}

void loop()
{
  long red = particleSensor.getRed();  //Rojo
  long ir = particleSensor.getIR();  //Infrarrojo

  bool fingerDetected = (ir >= 50000);
  float spo2 = 0;
  float R = 0;

  if (fingerDetected)
  {
    //  SpO2 
    R = (float)red / (float)ir;
    spo2 = 104.0 - 17.0 * R;

    //  BPM 
    if (checkForBeat(ir) == true)
    {
      long delta = millis() - lastBeat;
      lastBeat = millis();

      beatsPerMinute = 60000.0 / delta; // 60000 ms en un minuto

      if (beatsPerMinute < 255 && beatsPerMinute > 20)
      {
        rates[rateSpot++] = (byte)beatsPerMinute;
        rateSpot %= RATE_SIZE;

        // Recalcular promedio
        beatAvg = 0;
        for (byte x = 0; x < RATE_SIZE; x++)
          beatAvg += rates[x];
        beatAvg /= RATE_SIZE;
      }
    }

    debug.print("Red: "); debug.print(red);
    debug.print("\tIR: "); debug.print(ir);
    debug.print("\tSpO2: "); debug.print(spo2);
    debug.print("\tBPM: "); debug.print(beatsPerMinute);
    debug.print("\tBPM Avg: "); debug.println(beatAvg);
    delay(300); 
  }
  else
  {
    debug.println("No se detecta dedo");
    beatsPerMinute = 0;
    beatAvg = 0;
    delay(300); 
  }

  //  Lectura del botón con antirrebote 
  bool reading = digitalRead(BUTTON_PIN);

  if (reading != lastReading){
    lastDebounceTime = millis(); // reinicia el conteo
  }

  if((millis() - lastDebounceTime) > debounceDelay){
    if (reading != stableState){
      stableState = reading; // confirmar el cambio de estado

      if (stableState == LOW){ //botón presionado
        if (fingerDetected){
          String data ="SpO2:" + String(spo2, 1) + ",BPM:" + String(beatAvg);

          debug.println("Botón presionado. Enviando por BLE: " + data);

          if (deviceConnected){
            pTxCharacteristic->setValue(data.c_str());
            pTxCharacteristic->notify();
          }
          else{
            debug.println("No hay dispositivo BLE conectado.");
          }
        }
        else{
          debug.println("Botón presionado pero no hay dedo detectado.");
        }
      }
    }
  }

  lastReading = reading;

  delay(20); // Delay corto para no perder latidos 
}