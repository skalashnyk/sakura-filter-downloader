package org.kalashnyk.sakuradownloader;


import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * @author Sergii Kalashnyk
 * @version 1.0
 */

public class SakuraDownloader {
	private static BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
	private static final String URL_HOME = "http://www.sakurafilter.com";
	private static final String URL_FORMAT =  "http://www.sakurafilter.com/index.php/productdetail/index?snum=%s";
	private static final String REFFERER = "http://google.com";
	private static final String USER_AGENT = "Chrome/50.0.2661.75";

	public static void main(String[] args) throws Exception {
		new SakuraDownloader().run();
	}

	public void run() throws Exception {
		System.out.println("\nВведите полный путь к файлу каталога продуктов,\nнапример, 'D:/products/catalogue.csv' (без кавычек):");
		String catalogueFilePath = consoleReader.readLine();

		System.out.println("\nВведите путь для загрузки информации о продуктах,\n например, 'D:/products/Sakura' (без кавычек)");
		String pathName = consoleReader.readLine();

		Path notFoundProducts = Paths.get(pathName.substring(0, pathName.lastIndexOf('/') + 1) + "notFoundProducts.txt");
		if (Files.notExists(notFoundProducts)) {
			Files.createFile(notFoundProducts);
		}

		Random random = new Random();
		int max = 5000;
		int min = 2000;

		int step = 1;
		int stepBarrier = getProducts(catalogueFilePath).size();
		try (OutputStream notFoundProductsOutStream =
					 new FileOutputStream(notFoundProducts.toFile(), true);){
			for (String productName : getProducts(catalogueFilePath)) {
				System.out.print("Step " + step++ + " of " + stepBarrier + ": Starting download " + productName + "... ");
				Thread.sleep(random.nextInt(max - min) + min);
				try {
					Document document = getDocument(productName);
					download(pathName, productName, getProductDetails(document), getImagesUrls(document));
					System.out.println("Successfully!");
				} catch (HttpStatusException e){
					if (e.getStatusCode() == 404)
						System.out.println("Fail! " + productName + " is not found!");
					notFoundProductsOutStream.write((productName + System.lineSeparator()).getBytes());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println("\nExiting program...");
		System.out.println("\nGOOD BYE!");
	}

	private void download(String pathName,
						  String productName,
						  List<String> productDetails,
						  List<String> imagesUrl)
			throws Exception {

		Path imgProductPath = Paths.get(pathName + "/" + productName + "/img");
		if (Files.notExists(imgProductPath)) {
			Files.createDirectories(imgProductPath);
			int count = 1;
			for (String imagesUrls : imagesUrl) {
				String urlAddress = URL_HOME + imagesUrls;

				try {
					Path imgFile = Paths.get(imgProductPath + "/" + productName + "_" + count + ".png");
					if (Files.notExists(imgFile))
						Files.createFile(imgFile);

					count++;

					InputStream input = new URL(urlAddress).openStream();
					OutputStream out = Files.newOutputStream(imgFile, WRITE, APPEND);

					byte[] buff = new byte[1024 * 1024];
					int len;
					while ((len = input.read(buff)) != -1) {
						out.write(buff, 0, len);
					}
					input.close();
					out.close();
				} catch (Exception e) {
					System.out.println(productName);
					System.out.println("Exception during download images:");
					e.printStackTrace();
					System.out.println("-----------");
				}
			}
		}

		Path productDetailFile = Paths.get(pathName + "/" + productName + "/" + productName + ".csv");
		if (Files.notExists(productDetailFile)) {
			Files.createFile(productDetailFile);

			try (OutputStream out = Files.newOutputStream(productDetailFile, WRITE, APPEND)) {

				for (int i = 0; i < productDetails.size(); i++) {
					out.write((productDetails.get(i) + System.lineSeparator()).getBytes());
				}
			} catch (Exception e) {
				System.out.println(productName);
				System.out.println("Exception during write product details:");
				e.printStackTrace();
				System.out.println("-----------");
			}
		}
	}

	public List<String> getProductDetails(Document document) {
		List<String> productDetail = new ArrayList<>();

		Elements productDetailTable =
					document.getElementsByClass("column_left")
							.first().getElementsByTag("tr");


		for (Element element : productDetailTable) {
			if (element.children().size() < 3)
				continue;
			String key = element.child(0).ownText();
			String value = element.child(2).ownText();
			if (value.isEmpty())
				try {
					value = element.child(2).child(0).ownText();
				} catch (Exception e) {}
			productDetail.add(key + "; : ;" + value);
		}

		return productDetail;
	}

	private List<String> getImagesUrls(Document document) {
		List<String> imagesUrls = new ArrayList<>();

		for (Element e : document.select("img[src$=.png]")) {
			if (e.attr("src").contains("product"))
				imagesUrls.add(e.attr("src"));
		}
		return imagesUrls;
	}

	protected Document getDocument(String searchString) throws IOException {
		String url = String.format(URL_FORMAT, searchString);
		return Jsoup.connect(url)
					.userAgent(USER_AGENT)
					.referrer(REFFERER)
					.get();
	}

	private List<String> getProducts(String catalogueFilePath) {
		List<String> products = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(catalogueFilePath))){
			while (br.ready()) {
				products.add(br.readLine());
			}
		} catch (FileNotFoundException e) {
			System.out.println("File with products not found: '" + catalogueFilePath + "'" );
		} catch (IOException e) {
			System.out.println("Exception during reading file with products:");
			e.printStackTrace();
		}
		return products;
	}
}