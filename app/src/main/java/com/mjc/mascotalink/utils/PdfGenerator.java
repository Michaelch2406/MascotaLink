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
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
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
            }

            if (outputStream != null) {
                createPdf(context, outputStream, paseo);
                Toast.makeText(context, "Comprobante descargado en Descargas/Walki", Toast.LENGTH_LONG).show();
                
                if (file != null) {
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

        // Colors
        DeviceRgb primaryColor = new DeviceRgb(19, 164, 236); // Walki Blue
        DeviceRgb grayColor = new DeviceRgb(107, 114, 128);

        // Header Table (Logo + Info)
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        headerTable.setWidth(UnitValue.createPercentValue(100));

        // Logo Cell
        Drawable d = ContextCompat.getDrawable(context, R.drawable.walki_logo_principal);
        if (d != null) {
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] bitmapData = stream.toByteArray();
            ImageData imageData = ImageDataFactory.create(bitmapData);
            Image image = new Image(imageData);
            image.scaleToFit(80, 80);
            
            Cell logoCell = new Cell().add(image).setBorder(Border.NO_BORDER);
            logoCell.setVerticalAlignment(VerticalAlignment.MIDDLE);
            headerTable.addCell(logoCell);
        } else {
            headerTable.addCell(new Cell().add(new Paragraph("Walki")).setBorder(Border.NO_BORDER));
        }

        // Info Cell
        Paragraph headerInfo = new Paragraph()
                .add(new Text("COMPROBANTE DE PAGO\n").setBold().setFontSize(16).setFontColor(primaryColor))
                .add(new Text("Walki App\n").setFontSize(12).setFontColor(grayColor))
                .add(new Text("Fecha: " + paseo.getFechaFormateada() + "\n").setFontSize(10))
                .add(new Text("ID: " + paseo.getReservaId()).setFontSize(10));
        
        Cell infoCell = new Cell().add(headerInfo).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        headerTable.addCell(infoCell);

        document.add(headerTable);

        // Separator
        SolidLine line = new SolidLine(1f);
        line.setColor(primaryColor);
        LineSeparator ls = new LineSeparator(line);
        ls.setMarginTop(10);
        ls.setMarginBottom(20);
        document.add(ls);

        // Section: Detalles del Servicio
        document.add(new Paragraph("DETALLES DEL SERVICIO")
                .setBold()
                .setFontColor(primaryColor)
                .setFontSize(12)
                .setMarginBottom(5));

        Table serviceTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        serviceTable.setWidth(UnitValue.createPercentValue(100));
        serviceTable.setMarginBottom(15);

        addRow(serviceTable, "Paseador:", paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "-");
        addRow(serviceTable, "Dueño:", paseo.getDuenoNombre() != null ? paseo.getDuenoNombre() : "-");
        addRow(serviceTable, "Mascota:", paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "-");
        addRow(serviceTable, "Duración:", paseo.getDuracion_minutos() + " min");
        addRow(serviceTable, "Estado:", paseo.getEstado());

        document.add(serviceTable);

        // Section: Detalles del Pago
        document.add(new Paragraph("DETALLES DEL PAGO")
                .setBold()
                .setFontColor(primaryColor)
                .setFontSize(12)
                .setMarginBottom(5));

        Table payTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        payTable.setWidth(UnitValue.createPercentValue(100));

        addRow(payTable, "Método:", paseo.getMetodo_pago() != null ? paseo.getMetodo_pago() : "Desconocido");
        // Total Cost Row (Standout)
        Cell labelCell = new Cell().add(new Paragraph("Costo Total:")).setBorder(Border.NO_BORDER).setBold();
        Cell valueCell = new Cell().add(new Paragraph("$" + String.format(Locale.US, "%.2f", paseo.getCosto_total())))
                .setBorder(Border.NO_BORDER)
                .setBold()
                .setFontSize(14)
                .setFontColor(primaryColor);
        
        payTable.addCell(labelCell);
        payTable.addCell(valueCell);

        document.add(payTable);

        // Footer
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Gracias por confiar en Walki.")
                .setTextAlignment(TextAlignment.CENTER)
                .setItalic()
                .setFontColor(grayColor)
                .setFontSize(10));

        document.close();
    }

    private static void addRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label)).setBorder(Border.NO_BORDER).setBold().setFontSize(10));
        table.addCell(new Cell().add(new Paragraph(value)).setBorder(Border.NO_BORDER).setFontSize(10));
    }
}
