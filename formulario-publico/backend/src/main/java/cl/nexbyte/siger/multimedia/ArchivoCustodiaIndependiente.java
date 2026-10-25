package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.formulario.AdjuntosFormulario;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Lectura de originales institucionales y de una restauración independiente, nunca de URLs ciudadanas. */
public final class ArchivoCustodiaIndependiente {
    private final Path institutional, restored;

    public ArchivoCustodiaIndependiente(Path institutional, Path restored, Path publicStore) throws IOException {
        this.institutional = safe(institutional);
        this.restored = safe(restored);
        var local = safe(publicStore);
        for (var pair : List.of(List.of(this.institutional, this.restored), List.of(this.institutional, local), List.of(this.restored, local)))
            if (pair.get(0).startsWith(pair.get(1)) || pair.get(1).startsWith(pair.get(0)) || Files.isSameFile(pair.get(0), pair.get(1)))
                throw new IOException("Custodias independientes requeridas");
    }

    private static Path safe(Path value) throws IOException {
        Path p = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archivo institucional no montado");
        for (Path part = p; part != null; part = part.getParent())
            if (Files.isSymbolicLink(part) || Files.exists(part.resolve(".git"))) throw new IOException("Ruta privada sin enlaces requerida");
        return p.toRealPath();
    }

    /** Requisito de activación del CLI real: ambos montajes de lectura, separados del disco público y entre sí. */
    public void calificarMontajes(Path publicStore) throws IOException {
        var a = Files.getFileStore(institutional); var b = Files.getFileStore(restored); var p = Files.getFileStore(publicStore);
        if (!a.isReadOnly() || !b.isReadOnly() || a.equals(p) || b.equals(p) || a.equals(b))
            throw new IOException("Montajes de solo lectura y recuperación en medios independientes requeridos");
    }

    private Path file(Path root, UUID submission, UUID attachment) throws IOException {
        var path = root.resolve("objects").resolve(submission.toString()).resolve(attachment + ".bin");
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IOException("Enlace institucional no permitido");
        return path;
    }

    private static void verify(Path file, AdjuntosFormulario.Descriptor d) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != d.tamanio() || !AlmacenPrivado.huella(file).equals(d.sha256()))
            throw new IOException("Original institucional no recuperable o discrepante");
    }

    public void verificar(UUID submission, AdjuntosFormulario.Descriptor d, Path local) throws IOException {
        var a = file(institutional, submission, d.id()); var b = file(restored, submission, d.id());
        if (Files.isSameFile(a, b) || local != null && Files.exists(local) && (Files.isSameFile(a, local) || Files.isSameFile(b, local)))
            throw new IOException("Una copia pública no acredita recuperación independiente");
        verify(a, d); verify(b, d); // lectura completa, no HEAD ni confianza en metadatos
    }

    /** Exportación explícita a destino NUEVO. Nunca rehidrata automáticamente el SSD público. */
    public void recuperar(UUID submission, AdjuntosFormulario.Descriptor d, Path destination) throws IOException {
        var source = file(restored, submission, d.id()); verify(source, d);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destino nuevo requerido");
        Path parent = safe(destination.toAbsolutePath().getParent());
        if (parent.startsWith(institutional) || parent.startsWith(restored)) throw new IOException("No escribir sobre custodia");
        boolean success = false, created = false;
        try {
            var channel = java.nio.channels.FileChannel.open(destination, Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE), java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            created = true;
            try (channel; var in = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS); var out = java.nio.channels.Channels.newOutputStream(channel)) {
                in.transferTo(out);
            }
            Files.setPosixFilePermissions(destination, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            verify(destination, d);
            try (var durable = java.nio.channels.FileChannel.open(destination, StandardOpenOption.WRITE)) { durable.force(true); }
            AlmacenPrivado.sincronizar(parent); success = true;
        } finally { if (created && !success) Files.deleteIfExists(destination); }
    }
}