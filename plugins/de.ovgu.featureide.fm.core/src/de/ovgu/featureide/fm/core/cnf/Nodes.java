/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2016  FeatureIDE team, University of Magdeburg, Germany
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
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.core.cnf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Or;

/**
 * Several methods concerning {@link Node} framework.
 * 
 * @author Sebastian Krieter
 */
public final class Nodes {

	private Nodes() {
	}

	public static List<LiteralSet> convert(IVariables satInstance, Node node) {
		final ArrayList<LiteralSet> clauses = new ArrayList<>();
		CNFCreator.getClauseFromNode(satInstance, clauses, node);
		return clauses;
	}

	public static CNF convert(Node node) {
		final Set<Object> distinctVariableObjects = getDistinctVariableObjects(node);
		final ArrayList<String> variableList = new ArrayList<>(distinctVariableObjects.size());
		for (Object varObject : distinctVariableObjects) {
			if (varObject instanceof String) {
				variableList.add((String) varObject);
			}
		}
		final Variables mapping = new Variables(variableList);
		final List<LiteralSet> clauses = convert(mapping, node);
		return new CNF(mapping, clauses);
	}

	public static Node convert(CNF satInstance) {
		final List<LiteralSet> clauses = satInstance.getClauses();
		final Or[] nodeClauses = new Or[clauses.size()];
		int index = 0;
		for (LiteralSet clause : clauses) {
			nodeClauses[index++] = convert(satInstance, clause);
		}
		return new And(nodeClauses);
	}

	public static Or convert(IVariables satInstance, LiteralSet clause) {
		final int[] literals = clause.getLiterals();
		final Literal[] nodeLiterals = new Literal[literals.length];
		for (int i = 0; i < literals.length; i++) {
			final int literal = literals[i];
			nodeLiterals[i] = new Literal(satInstance.getName(literal), literal > 0);
		}
		return new Or(nodeLiterals);
	}

	public static Set<Object> getDistinctVariableObjects(Node cnf) {
		final HashSet<Object> result = new HashSet<>();
		for (Node clause : cnf.getChildren()) {
			final Node[] literals = clause.getChildren();
			for (int i = 0; i < literals.length; i++) {
				result.add(((Literal) literals[i]).var);
			}
		}
		return result;
	}

}
