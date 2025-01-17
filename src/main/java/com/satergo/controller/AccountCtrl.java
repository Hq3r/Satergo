package com.satergo.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.satergo.*;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenBalance;
import com.satergo.ergouri.ErgoURIString;
import com.satergo.extra.IncorrectPasswordException;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatTextInputDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import javafx.util.StringConverter;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.ErgoId;
import org.ergoplatform.appkit.Mnemonic;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AccountCtrl implements Initializable, WalletTab {

	@FXML private Label walletName, totalBalanceLabel, totalBalance;
	@FXML private VBox tokens;
	@FXML private VBox addresses;

	@FXML private ImageView qrCodeImage;
	@FXML private Hyperlink saveQrCode;
	@FXML private ComboBox<Integer> qrCodeAddress;

	private void retrieveSeed() {
		WalletKey.Local key = (WalletKey.Local) Main.get().getWallet().key();
		try {
			SatVoidDialog dialog = new SatVoidDialog();
			dialog.initOwner(totalBalance.getScene().getWindow());
			dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
			Main.get().applySameTheme(dialog.getScene());
			dialog.setTitle("Your seed");
			dialog.setHeaderText("Your seed");
			Mnemonic mnemonic = key.getMnemonic();
			if (mnemonic.getPassword().isEmpty()) {
				dialog.getDialogPane().setContent(new Label(mnemonic.getPhrase().toStringUnsecure()));
				ButtonType copy = new ButtonType(Main.lang("copy"));
				dialog.getDialogPane().getButtonTypes().add(copy);
				dialog.showForResult().ifPresent(t -> {
					if (t == copy) Utils.copyStringToClipboard(mnemonic.getPhrase().toStringUnsecure());
				});
			} else {
				GridPane grid = new GridPane();
				grid.add(new Label("Phrase: "), 0, 0);
				grid.add(new Label(mnemonic.getPhrase().toStringUnsecure()), 1, 0);
				grid.add(new Label("Password: "), 0, 1);
				grid.add(new Label(mnemonic.getPassword().toStringUnsecure()), 1, 1);
				dialog.getDialogPane().setContent(grid);
				ButtonType copyPhrase = new ButtonType("Copy phrase");
				ButtonType copyPassword = new ButtonType("Copy password");
				dialog.getDialogPane().getButtonTypes().addAll(copyPhrase, copyPassword);
				dialog.showForResult().ifPresent(t -> {
					if (t == copyPhrase) Utils.copyStringToClipboard(mnemonic.getPhrase().toStringUnsecure());
					else if (t == copyPassword) Utils.copyStringToClipboard(mnemonic.getPassword().toStringUnsecure());
				});
			}
		} catch (WalletKey.Failure ignored) {
			// user already informed
		}
	}

	private void changeWalletName() {
		SatTextInputDialog dialog = new SatTextInputDialog();
		dialog.initOwner(Main.get().stage());
		dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		Main.get().applySameTheme(dialog.getScene());
		dialog.setTitle(Main.lang("renameWallet"));
		dialog.setHeaderText(null);
		dialog.getEditor().setPromptText(Main.lang("walletName"));
		dialog.showForResult().ifPresent(newName -> Main.get().getWallet().name.set(newName));
	}

	private void changeWalletPassword() {
		// Create the custom dialog.
		SatPromptDialog<Pair<String, String>> dialog = new SatPromptDialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		Main.get().applySameTheme(dialog.getScene());
		dialog.setTitle(Main.lang("programName"));
		dialog.setHeaderText(Main.lang("changePassword"));

		// Set the button types.
		ButtonType changeType = new ButtonType(Main.lang("change"), ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(changeType, ButtonType.CANCEL);

		// Create the currentPassword and newPassword labels and fields.
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		PasswordField currentPassword = new PasswordField();
		PasswordField newPassword = new PasswordField();

		grid.add(new Label(Main.lang("currentC")), 0, 0);
		grid.add(currentPassword, 1, 0);
		grid.add(new Label(Main.lang("newC")), 0, 1);
		grid.add(newPassword, 1, 1);

		Node changeButton = dialog.getDialogPane().lookupButton(changeType);
		changeButton.setDisable(true);
		currentPassword.textProperty().addListener((observable, oldValue, newValue) -> {
			changeButton.setDisable(newValue.trim().isEmpty());
		});
		dialog.getDialogPane().setContent(grid);
		Platform.runLater(currentPassword::requestFocus);
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == changeType) {
				return new Pair<>(currentPassword.getText(), newPassword.getText());
			}
			return null;
		});

		Pair<String, String> result = dialog.showForResult().orElse(null);
		if (result == null) return;
		try {
			Main.get().getWallet().changePassword(result.getKey().toCharArray(), result.getValue().toCharArray());
		} catch (IncorrectPasswordException ex) {
			Utils.alertIncorrectPassword();
		}
	}

	@FXML
	public void addAddress(ActionEvent e) {
		int nextIndex = Main.get().getWallet().nextAddressIndex();
		SatPromptDialog<Pair<Integer, String>> dialog = new SatPromptDialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		Main.get().applySameTheme(dialog.getScene());
		dialog.setTitle(Main.lang("addAddress"));
		dialog.setHeaderText(null);
		var content = new HBox() {
			{ Load.thisFxml(this, "/dialog/add-address.fxml"); }
			@FXML TextField index, name;
		};
		content.index.setText(String.valueOf(nextIndex));
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setResultConverter(t -> {
			if (t == ButtonType.OK) {
				return new Pair<>(content.index.getText().isBlank() ? null : Integer.parseInt(content.index.getText()), content.name.getText());
			}
			return null;
		});
		Pair<Integer, String> result = dialog.showForResult().orElse(null);
		if (result == null) return;
		if (Main.get().getWallet().myAddresses.containsKey(result.getKey())) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("anAddressWithThisIndexAlreadyExists"));
			return;
		}
		if (result.getKey() < 0) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("invalidAddressIndex"));
			return;
		}
		int index = Objects.requireNonNullElse(result.getKey(), nextIndex);
		Main.get().getWallet().myAddresses.put(index, result.getValue());
	}

	@FXML
	public void logout(ActionEvent e) {
		Main.get().getWalletPage().cancelRepeatingTasks();
		Main.get().setWallet(null);
		Main.get().displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE)
			Main.node.stop();
	}

	@FXML
	public void openSettingsDialog(ActionEvent e) {
		SatVoidDialog dialog = new SatVoidDialog();
		dialog.initOwner(totalBalance.getScene().getWindow());
		Main.get().applySameTheme(dialog.getScene());
		dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		dialog.getDialogPane().setPrefWidth(Math.min(500, Main.get().stage().getWidth() * 0.5));
		dialog.setTitle("Wallet settings");
		dialog.setHeaderText("Wallet settings");

		VBox root = new VBox(4);

		Button changeName = new Button("Change name");
		changeName.setMaxWidth(Double.POSITIVE_INFINITY);
		changeName.setOnAction(ae -> {
			dialog.close();
			changeWalletName();
		});

		Button changePass = new Button("Change password");
		changePass.setMaxWidth(Double.POSITIVE_INFINITY);
		changePass.setOnAction(ae -> {
			dialog.close();
			changeWalletPassword();
		});

		Button viewSeed = new Button("View seed");
		viewSeed.setMaxWidth(Double.POSITIVE_INFINITY);
		viewSeed.setOnAction(ae -> {
			dialog.close();
			retrieveSeed();
		});

		root.getChildren().addAll(changeName, changePass);
		boolean isLocalWallet = Main.get().getWallet().key() instanceof WalletKey.Local;
		if (isLocalWallet) root.getChildren().add(viewSeed);

		dialog.getDialogPane().setContent(root);
		dialog.show();
	}

	private static class AddressLine extends GridPane {
		@FXML private Label index, name, address;
		@FXML private Button copy, rename, remove;

		public AddressLine(int index, String name, Address address, Runnable removed, Consumer<String> renamed, boolean removable) {
			Load.thisFxml(this, "/line/account-address.fxml");
			this.index.setText("#" + index);
			this.name.setText(name);
			this.address.setText(address.toString());
			this.copy.setOnAction(e -> {
				Utils.copyStringToClipboard(address.toString());
				Utils.showTemporaryTooltip(copy, new Tooltip(Main.lang("copied")), 400);
			});
			this.rename.setOnAction(e -> {
				SatTextInputDialog dialog = new SatTextInputDialog();
				dialog.initOwner(Main.get().stage());
				dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
				Main.get().applySameTheme(dialog.getScene());
				dialog.setTitle(Main.lang("renameAddress"));
				dialog.setHeaderText(null);
				dialog.getEditor().setPromptText(Main.lang("addressName"));
				String newName = dialog.showForResult().orElse(null);
				if (newName == null) return;
				this.name.setText(newName);
				renamed.accept(newName);
			});
			if (removable) {
				this.remove.setOnAction(e -> removed.run());
			} else {
				this.remove.setDisable(true);
			}
		}
	}

	private static class TokenLine extends BorderPane {
		private final TokenBalance token;
		@FXML private Label name, symbol, amount;
		@FXML private ImageView icon;
		@FXML private Button copyId;

		public TokenLine(TokenBalance token) {
			this.token = token;
			Load.thisFxml(this, "/line/account-token.fxml");
			this.name.setText(token.name() == null ? Main.lang("unnamed_parentheses") : token.name());
			this.icon.setImage(Utils.tokenIcon36x36(ErgoId.create(token.id())));
			this.amount.setText(ErgoInterface.fullTokenAmount(token.amount(), token.decimals()).toPlainString());
		}

		@FXML
		public void copyId(ActionEvent e) {
			Utils.copyStringToClipboard(token.id());
			Utils.showTemporaryTooltip(copyId, new Tooltip(Main.lang("copied")), 400);
		}
	}

	private void updateAddresses() {
		addresses.getChildren().clear();
		Main.get().getWallet().myAddresses.forEach((index, name) -> {
			try {
				addresses.getChildren().add(new AddressLine(index, name, Main.get().getWallet().publicAddress(index),
						() -> Main.get().getWallet().myAddresses.remove(index),
						newName -> Main.get().getWallet().myAddresses.put(index, newName), index != 0));
			} catch (WalletKey.Failure e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		walletName.textProperty().bind(Main.get().getWallet().name);
		totalBalanceLabel.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		totalBalance.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		tokens.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		DecimalFormat lossless = new DecimalFormat("0");
		lossless.setMinimumFractionDigits(9);
		lossless.setMaximumFractionDigits(9);
		if (Main.get().getWallet().lastKnownBalance.get() != null) {
			totalBalance.setText(lossless.format(ErgoInterface.toFullErg(Main.get().getWallet().lastKnownBalance.get().confirmed())) + " ERG");
			for (TokenBalance token : Main.get().getWallet().lastKnownBalance.get().confirmedTokens()) {
				TokenLine tokenLine = new TokenLine(token);
				tokens.getChildren().add(tokenLine);
			}
		}
		// binding with a converter could be used here
		Main.get().getWallet().lastKnownBalance.addListener((observable, oldValue, newValue) -> {
			totalBalance.setText(lossless.format(ErgoInterface.toFullErg(newValue.confirmed())) + " ERG");
			tokens.getChildren().clear();
			for (TokenBalance token : Main.get().getWallet().lastKnownBalance.get().confirmedTokens()) {
				TokenLine tokenLine = new TokenLine(token);
				tokens.getChildren().add(tokenLine);
			}
		});
		updateAddresses();
		qrCodeAddress.getItems().addAll(Main.get().getWallet().myAddresses.keySet());
		qrCodeAddress.setValue(0);

		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> {
			updateAddresses();
			qrCodeAddress.getItems().setAll(Main.get().getWallet().myAddresses.keySet());
			qrCodeAddress.setValue(Main.get().getWallet().myAddresses.size() - 1);
		});

		qrCodeAddress.setConverter(new StringConverter<>() {
			@Override
			public String toString(Integer index) {
				return index == null ? null : Main.get().getWallet().myAddresses.get(index);
			}

			@Override
			public Integer fromString(String string) {
				throw new RuntimeException();
			}
		});
		qrCodeAddress.setOnAction(e -> {
			if (qrCodeAddress.getValue() == null) {
				qrCodeImage.setVisible(false);
				return;
			}
			try {
				qrCodeImage.setVisible(true);
				qrCode(Main.get().getWallet().publicAddress(qrCodeAddress.getValue()).toString());
			} catch (WriterException | WalletKey.Failure ex) {
				ex.printStackTrace();
				qrCodeImage.setVisible(false);
			}
		});

		try {
			qrCode(Main.get().getWallet().publicAddress(0).toString());
		} catch (WriterException | WalletKey.Failure e) {
			throw new RuntimeException(e);
		}
	}

	private void qrCode(String address) throws WriterException {
		QRCodeWriter qrCodeWriter = new QRCodeWriter();
		int size = 200;
		ErgoURIString ergoURI = new ErgoURIString(address, null);
		BitMatrix bitMatrix = qrCodeWriter.encode(ergoURI.toString(), BarcodeFormat.QR_CODE, size, size, Map.of(EncodeHintType.MARGIN, 1));
		WritableImage img = new WritableImage(size, size);
		PixelWriter writer = img.getPixelWriter();
		for (int readY = 0; readY < size; readY++) {
			for (int readX = 0; readX < size; readX++) {
				writer.setColor(readX, readY, bitMatrix.get(readX, readY) ? Color.BLACK : Color.WHITE);
			}
		}

		qrCodeImage.setImage(img);

		saveQrCode.setOnAction(e -> {
			Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("saveQrCode"), "qr-code.png",
					new FileChooser.ExtensionFilter(Main.lang("pngImage"), "*.png"),
					new FileChooser.ExtensionFilter(Main.lang("jpegImage"), "*.jpg"));
			if (path == null) return;
			RenderedImage renderedImage = SwingFXUtils.fromFXImage(img, null);
			String format = "png";
			if (path.getFileName().toString().endsWith(".jpg") || path.getFileName().toString().endsWith(".jpeg"))
				format = "jpg";
			try {
				ImageIO.write(renderedImage, format, path.toFile());
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		});
	}
}
