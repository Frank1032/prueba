# App Animales EfficientNet

Aplicación Android de la **Tarea 3 de Procesamiento Digital de Imágenes (UdeA)**: CNN moderna EfficientNetB0, escalamiento compuesto y visión móvil.

La app usa la red EfficientNetB0 que entrenamos para reconocer **araña, ardilla, gallina, gato y vaca**. La red va dentro del celular: funciona sin internet.

| Dato | Valor |
|---|---|
| Red | EfficientNetB0 (transfer learning + fine-tuning) |
| Archivo de pesos | `app/src/main/assets/red_animales_16bits.tflite` |
| Tamaño | 8,1 MB (pesos de 16 bits) |
| Acierto en fotos de prueba | 98,9 % (742 de 750) |

## Cómo instalarla en el celular

1. Desde el celular Android, entre a la pestaña **Releases** de este repositorio.
2. Descargue `AnimalesEfficientNet.apk`.
3. Ábralo. Si el celular pregunta, permita **instalar apps de origen desconocido** para el navegador o el administrador de archivos.
4. Abra **Animales EfficientNet**, tome una foto o elija una de la galería.

## Cómo se genera el APK

Cada vez que se sube un cambio a la rama `app-animales`, GitHub Actions compila la app (`.github/workflows/compilar-apk.yml`) y publica el APK en **Releases**.

## Código

- `app/src/main/java/co/edu/udea/animales/MainActivity.java`: lleva la foto a 224 × 224, se la pasa a la red y muestra el porcentaje de cada animal.
- `app/src/main/res/layout/activity_main.xml`: la pantalla.
