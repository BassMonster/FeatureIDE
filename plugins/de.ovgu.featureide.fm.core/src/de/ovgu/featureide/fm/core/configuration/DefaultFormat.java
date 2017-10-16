/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2017  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 *
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.fm.core.configuration;

import static de.ovgu.featureide.fm.core.localization.StringTable.CANNOT_BE_SELECTED;
import static de.ovgu.featureide.fm.core.localization.StringTable.DOES_NOT_EXIST;
import static de.ovgu.featureide.fm.core.localization.StringTable.FEATURE;
import static de.ovgu.featureide.fm.core.localization.StringTable.FEATURE_;
import static de.ovgu.featureide.fm.core.localization.StringTable.IS_CORRUPT__NO_ENDING_QUOTATION_MARKS_FOUND_;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.StringTokenizer;

import de.ovgu.featureide.fm.core.PluginID;
import de.ovgu.featureide.fm.core.io.IConfigurationFormat;
import de.ovgu.featureide.fm.core.io.IPersistentFormat;
import de.ovgu.featureide.fm.core.io.Problem;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.localization.StringTable;

/**
 * Simple configuration format.<br/> Lists all selected features in the user-defined order (if specified).
 *
 * @author Sebastian Krieter
 */
public class DefaultFormat implements IConfigurationFormat {

	public static final String ID = PluginID.PLUGIN_ID + ".format.config." + DefaultFormat.class.getSimpleName();

	private static final String NEWLINE = System.lineSeparator();

	@Override
	public ProblemList read(Configuration configuration, CharSequence source) {
		final ProblemList warnings = new ProblemList();

		String line = null;
		int lineNumber = 1;
		try (BufferedReader reader = new BufferedReader(new StringReader(source.toString()))) {
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("#") || line.isEmpty() || line.equals(" ")) {
					continue;
				}
				// the string tokenizer is used to also support the expression
				// format used by FeatureHouse
				final StringTokenizer tokenizer = new StringTokenizer(line);
				while (tokenizer.hasMoreTokens()) {
					String name = tokenizer.nextToken(" ");
					if (name.startsWith("\"")) {
						try {
							name = name.substring(1);
							name += tokenizer.nextToken("\"");
							if (!tokenizer.nextToken(" ").startsWith("\"")) {
								warnings.add(new Problem(FEATURE_ + name + IS_CORRUPT__NO_ENDING_QUOTATION_MARKS_FOUND_, lineNumber));
							}
						} catch (final RuntimeException e) {
							warnings.add(new Problem(FEATURE_ + name + IS_CORRUPT__NO_ENDING_QUOTATION_MARKS_FOUND_, lineNumber));
						}
					}
					try {
						configuration.setManual(name, Selection.SELECTED);
					} catch (final FeatureNotFoundException e) {
						warnings.add(new Problem(FEATURE + name + DOES_NOT_EXIST, lineNumber));
					} catch (final SelectionNotPossibleException e) {
						warnings.add(new Problem(FEATURE + name + CANNOT_BE_SELECTED, lineNumber));
					}
				}
				lineNumber++;
			}
		} catch (final IOException e) {
			warnings.clear();
			warnings.add(new Problem(e));
			return warnings;
		}
		return warnings;
	}

	public String readLine(String line) {
		return null;
	}

	@Override
	public String write(Configuration configuration) {
		final StringBuilder buffer = new StringBuilder();
		writeSelectedFeatures(configuration.getRoot(), buffer);
		return buffer.toString();
	}

	private void writeSelectedFeatures(SelectableFeature feature, StringBuilder buffer) {
		if (feature.getFeature().getStructure().isConcrete() && (feature.getSelection() == Selection.SELECTED)) {
			if (feature.getName().contains(" ")) {
				buffer.append("\"" + feature.getName() + "\"" + NEWLINE);
			} else {
				buffer.append(feature.getName() + NEWLINE);
			}
		}
		for (final TreeElement child : feature.getChildren()) {
			writeSelectedFeatures((SelectableFeature) child, buffer);
		}
	}

	@Override
	public String getSuffix() {
		return StringTable.CONFIG;
	}

	@Override
	public IPersistentFormat<Configuration> getInstance() {
		return this;
	}

	@Override
	public boolean supportsRead() {
		return true;
	}

	@Override
	public boolean supportsWrite() {
		return true;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean supportsContent(CharSequence content) {
		return supportsRead();
	}

	@Override
	public String getName() {
		return "FeatureList";
	}

}
