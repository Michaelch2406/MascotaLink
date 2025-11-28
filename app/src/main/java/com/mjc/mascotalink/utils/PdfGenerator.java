package com.mjc.mascotalink.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.mjc.mascotalink.Paseo;
import com.mjc.mascotalink.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PdfGenerator {

    private static final String TAG = "PdfGenerator";

    public static Uri generarComprobante(Context context, Paseo paseo) {
        String fileName = "Comprobante_" + paseo.getReservaId() + ".pdf";
        OutputStream outputStream = null;
        Uri fileUri = null;
        File file = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Walki");

                fileUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (fileUri != null) {
                    outputStream = context.getContentResolver().openOutputStream(fileUri);
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File appDir = new File(downloadsDir, "Walki");
                if (!appDir.exists()) appDir.mkdirs();
                file = new File(appDir, fileName);
                outputStream = new FileOutputStream(file);
                // For pre-Q, we return a file URI later if needed, or use FileProvider
            }

            if (outputStream != null) {
                createPdf(context, outputStream, paseo);
                Toast.makeText(context, "Comprobante descargado en Descargas/Walki", Toast.LENGTH_LONG).show();
                
                if (file != null) {
                    // Return file URI for pre-Q (FileProvider logic handled in Activity)
                    return Uri.fromFile(file);
                }
                return fileUri;
            } else {
                Toast.makeText(context, "Error al crear archivo", Toast.LENGTH_SHORT).show();
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error generando PDF", e);
            Toast.makeText(context, "Error al generar PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void createPdf(Context context, OutputStream outputStream, Paseo paseo) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Add Logo
        Drawable d = ContextCompat.getDrawable(context, R.drawable.walki_logo_principal);
        if (d != null) {
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] bitmapData = stream.toByteArray();
            ImageData imageData = ImageDataFactory.create(bitmapData);
            Image image = new Image(imageData);
            image.scaleToFit(100, 100);
            image.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
            document.add(image);
        }

        // Título
        document.add(new Paragraph("COMPROBANTE DE PAGO")
                .setTextAlignment(TextAlignment.CENTER)
                .setBold()
                .setFontSize(20));
        
        document.add(new Paragraph("Walki")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(12)
                .setFontColor(ColorConstants.GRAY));

        document.add(new Paragraph("\n"));

        // Info de Reserva
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        table.setWidth(UnitValue.createPercentValue(100));

        addCell(table, "ID Reserva:", true);
        addCell(table, paseo.getReservaId(), false);

        addCell(table, "Fecha:", true);
        addCell(table, paseo.getFechaFormateada() + " " + paseo.getHoraFormateada(), false);
        
        addCell(table, "Estado:", true);
        addCell(table, paseo.getEstado(), false);

        document.add(table);
        document.add(new Paragraph("\nDETALLES DEL SERVICIO").setBold());
        
        Table serviceTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        serviceTable.setWidth(UnitValue.createPercentValue(100));

        addCell(serviceTable, "Paseador:", true);
        addCell(serviceTable, paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "-", false);
        
        addCell(serviceTable, "Dueño:", true);
        addCell(serviceTable, paseo.getDuenoNombre() != null ? paseo.getDuenoNombre() : "-", false);

        addCell(serviceTable, "Mascota:", true);
        addCell(serviceTable, paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "-", false);

        addCell(serviceTable, "Duración:", true);
        addCell(serviceTable, paseo.getDuracion_minutos() + " min", false);

        document.add(serviceTable);
        document.add(new Paragraph("\nDETALLES DEL PAGO").setBold());

        Table payTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        payTable.setWidth(UnitValue.createPercentValue(100));

        addCell(payTable, "Costo Total:", true);
        addCell(payTable, "$" + String.format(Locale.US, "%.2f", paseo.getCosto_total()), false);
        
        addCell(payTable, "Método:", true);
        addCell(payTable, paseo.getMetodo_pago() != null ? paseo.getMetodo_pago() : "Desconocido", false);
        
        if (paseo.getTransaction_id() != null) {
            addCell(payTable, "ID Transacción:", true);
            addCell(payTable, paseo.getTransaction_id(), false);
        }

        document.add(payTable);

        document.add(new Paragraph("\n\nGracias por confiar en Walki.")
                .setTextAlignment(TextAlignment.CENTER)
                .setItalic()
                .setFontSize(10));

        document.close();
    }

    private static void addCell(Table table, String text, boolean bold) {
        Paragraph p = new Paragraph(text);
        if (bold) p.setBold();
        table.addCell(new Cell().add(p).setBorder(Border.NO_BORDER));
    }
}
