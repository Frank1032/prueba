package co.edu.udea.animales;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int TOMAR_FOTO = 1;
    private static final int ELEGIR_FOTO = 2;
    private static final int TAMANO = 224;   // el mismo tamaño del entrenamiento

    private Interpreter red;
    private final List<String> clases = new ArrayList<>();

    private ImageView foto;
    private TextView aviso, animal, confianza, duda, tiempo;
    private TextView[] nombres, valores;
    private ProgressBar[] barras;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        foto = findViewById(R.id.foto);
        aviso = findViewById(R.id.aviso);
        animal = findViewById(R.id.animal);
        confianza = findViewById(R.id.confianza);
        duda = findViewById(R.id.duda);
        tiempo = findViewById(R.id.tiempo);

        try {
            cargarRed();
        } catch (IOException error) {
            Toast.makeText(this, "No se pudo cargar la red", Toast.LENGTH_LONG).show();
            return;
        }
        armarBarras();

        findViewById(R.id.botonTomar).setOnClickListener(v -> tomarFoto());
        findViewById(R.id.botonGaleria).setOnClickListener(v -> elegirFoto());
    }

    // Carga los pesos guardados de la red y el orden de las clases
    private void cargarRed() throws IOException {
        byte[] pesos = leerArchivo("red_animales_16bits.tflite");
        ByteBuffer modelo = ByteBuffer.allocateDirect(pesos.length).order(ByteOrder.nativeOrder());
        modelo.put(pesos);
        modelo.rewind();
        red = new Interpreter(modelo);

        BufferedReader lector = new BufferedReader(
                new InputStreamReader(getAssets().open("clases.txt"), StandardCharsets.UTF_8));
        String linea;
        while ((linea = lector.readLine()) != null) {
            if (!linea.trim().isEmpty()) clases.add(linea.trim());
        }
        lector.close();
    }

    private byte[] leerArchivo(String nombre) throws IOException {
        InputStream entrada = getAssets().open(nombre);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] pedazo = new byte[16384];
        int leidos;
        while ((leidos = entrada.read(pedazo)) != -1) {
            salida.write(pedazo, 0, leidos);
        }
        entrada.close();
        return salida.toByteArray();
    }

    // Una fila por animal: nombre, barra y porcentaje
    private void armarBarras() {
        LinearLayout contenedor = findViewById(R.id.barras);
        int n = clases.size();
        nombres = new TextView[n];
        valores = new TextView[n];
        barras = new ProgressBar[n];
        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < n; i++) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(0, (int) (5 * dp), 0, (int) (5 * dp));

            nombres[i] = new TextView(this);
            nombres[i].setText(clases.get(i));
            nombres[i].setTextSize(15);
            nombres[i].setTextColor(getColor(R.color.tinta_suave));
            fila.addView(nombres[i], new LinearLayout.LayoutParams((int) (76 * dp), LinearLayout.LayoutParams.WRAP_CONTENT));

            barras[i] = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            barras[i].setMax(1000);
            barras[i].setProgressTintList(ColorStateList.valueOf(getColor(R.color.acento)));
            barras[i].setProgressBackgroundTintList(ColorStateList.valueOf(getColor(R.color.barra)));
            LinearLayout.LayoutParams medidaBarra = new LinearLayout.LayoutParams(0, (int) (12 * dp), 1f);
            fila.addView(barras[i], medidaBarra);

            valores[i] = new TextView(this);
            valores[i].setText("–");
            valores[i].setTextSize(15);
            valores[i].setGravity(Gravity.END);
            valores[i].setTextColor(getColor(R.color.tinta));
            fila.addView(valores[i], new LinearLayout.LayoutParams((int) (78 * dp), LinearLayout.LayoutParams.WRAP_CONTENT));

            contenedor.addView(fila);
        }
    }

    private void tomarFoto() {
        Intent camara = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(camara, TOMAR_FOTO);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No se encontró la cámara. Use la galería.", Toast.LENGTH_LONG).show();
        }
    }

    private void elegirFoto() {
        Intent galeria = new Intent(Intent.ACTION_GET_CONTENT);
        galeria.setType("image/*");
        startActivityForResult(Intent.createChooser(galeria, "Elegir foto"), ELEGIR_FOTO);
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        super.onActivityResult(pedido, resultado, datos);
        if (resultado != RESULT_OK || datos == null) return;

        Bitmap imagen = null;
        if (pedido == TOMAR_FOTO && datos.getExtras() != null) {
            imagen = (Bitmap) datos.getExtras().get("data");
        } else if (pedido == ELEGIR_FOTO && datos.getData() != null) {
            imagen = abrirImagen(datos.getData());
        }

        if (imagen == null) {
            Toast.makeText(this, "No se pudo abrir la foto", Toast.LENGTH_LONG).show();
            return;
        }
        foto.setImageBitmap(imagen);
        aviso.setVisibility(View.GONE);
        reconocer(imagen);
    }

    // Abre la foto de la galería sin gastar mucha memoria y la endereza si viene girada
    private Bitmap abrirImagen(Uri direccion) {
        try {
            BitmapFactory.Options medidas = new BitmapFactory.Options();
            medidas.inJustDecodeBounds = true;
            InputStream entrada = getContentResolver().openInputStream(direccion);
            BitmapFactory.decodeStream(entrada, null, medidas);
            entrada.close();

            int reduccion = 1;
            while (Math.max(medidas.outWidth, medidas.outHeight) / reduccion > 1200) {
                reduccion *= 2;
            }
            BitmapFactory.Options opciones = new BitmapFactory.Options();
            opciones.inSampleSize = reduccion;
            entrada = getContentResolver().openInputStream(direccion);
            Bitmap imagen = BitmapFactory.decodeStream(entrada, null, opciones);
            entrada.close();
            if (imagen == null) return null;

            entrada = getContentResolver().openInputStream(direccion);
            ExifInterface exif = new ExifInterface(entrada);
            int orientacion = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            entrada.close();

            int grados = 0;
            if (orientacion == ExifInterface.ORIENTATION_ROTATE_90) grados = 90;
            if (orientacion == ExifInterface.ORIENTATION_ROTATE_180) grados = 180;
            if (orientacion == ExifInterface.ORIENTATION_ROTATE_270) grados = 270;
            if (grados != 0) {
                Matrix giro = new Matrix();
                giro.postRotate(grados);
                imagen = Bitmap.createBitmap(imagen, 0, 0, imagen.getWidth(), imagen.getHeight(), giro, true);
            }
            return imagen;
        } catch (Exception error) {
            return null;
        }
    }

    // Le pasa la foto a la red y muestra la respuesta
    private void reconocer(Bitmap imagen) {
        // 1. La foto se lleva a 224 x 224, igual que en el entrenamiento
        Bitmap pequena = Bitmap.createScaledBitmap(imagen, TAMANO, TAMANO, true);
        int[] pixeles = new int[TAMANO * TAMANO];
        pequena.getPixels(pixeles, 0, TAMANO, 0, 0, TAMANO, TAMANO);

        // 2. Cada píxel se separa en rojo, verde y azul (de 0 a 255)
        ByteBuffer entrada = ByteBuffer.allocateDirect(4 * TAMANO * TAMANO * 3).order(ByteOrder.nativeOrder());
        for (int pixel : pixeles) {
            entrada.putFloat((pixel >> 16) & 0xFF);
            entrada.putFloat((pixel >> 8) & 0xFF);
            entrada.putFloat(pixel & 0xFF);
        }
        entrada.rewind();

        // 3. La red analiza la foto y devuelve un porcentaje por animal
        float[][] salida = new float[1][clases.size()];
        long inicio = System.nanoTime();
        red.run(entrada, salida);
        long milisegundos = (System.nanoTime() - inicio) / 1000000;
        float[] probabilidades = salida[0];

        // 4. El animal con el porcentaje más alto es la respuesta
        int ganadora = 0;
        for (int i = 1; i < probabilidades.length; i++) {
            if (probabilidades[i] > probabilidades[ganadora]) ganadora = i;
        }

        animal.setText(clases.get(ganadora));
        confianza.setText(porcentaje(probabilidades[ganadora]));
        duda.setVisibility(probabilidades[ganadora] < 0.6f ? View.VISIBLE : View.GONE);
        tiempo.setText("Tiempo de la red en este celular: " + milisegundos + " ms");

        for (int i = 0; i < probabilidades.length; i++) {
            barras[i].setProgress(Math.round(probabilidades[i] * 1000));
            valores[i].setText(porcentaje(probabilidades[i]));
            boolean gana = (i == ganadora);
            nombres[i].setTextColor(getColor(gana ? R.color.tinta : R.color.tinta_suave));
            nombres[i].setTypeface(null, gana ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private String porcentaje(float valor) {
        return String.format(new Locale("es", "CO"), "%.1f %%", valor * 100);
    }

    @Override
    protected void onDestroy() {
        if (red != null) red.close();
        super.onDestroy();
    }
}
