package org.bxwbb.Util.DragDrop;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SystemFileTransferable implements Transferable {
    private final List<File> files;

    private final DataFlavor[] supportedFlavors = {
            DataFlavor.javaFileListFlavor,
            new DataFlavor("application/x-java-file-list;class=java.util.List")
    };

    public SystemFileTransferable(List<File> files) throws ClassNotFoundException {
        this.files = new ArrayList<>(files);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return supportedFlavors.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor supported : supportedFlavors) {
            if (supported.equals(flavor) || supported.getMimeType().equals(flavor.getMimeType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return java.util.Collections.unmodifiableList(files);
    }
}
