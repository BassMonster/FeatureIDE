/* FeatureIDE - An IDE to support feature-oriented software development
 * Copyright (C) 2005-2011  FeatureIDE Team, University of Magdeburg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 *
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.fm.core;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.CheckForNull;

import org.eclipse.core.runtime.IProgressMonitor;
import org.prop4j.And;
import org.prop4j.Implies;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Not;
import org.prop4j.Or;
import org.prop4j.SatSolver;
import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.fm.core.editing.Comparison;
import de.ovgu.featureide.fm.core.editing.ModelComparator;
import de.ovgu.featureide.fm.core.editing.NodeCreator;

/**
 * A collection of methods for working with {@link FeatureModel} will replace
 * the corresponding methods in {@link FeatureModel}
 * 
 * @author Soenke Holthusen
 */
public class FeatureModelAnalyser {
	
	private FeatureModel fm;

	/**
	 * A flag indicating that the calculation should be canceled.
	 */
	private boolean cancel = false;

	@CheckForNull
	private IProgressMonitor monitor;
	
	/**
     * 
     */
	protected FeatureModelAnalyser(FeatureModel fm) {
		this.fm = fm;
	}

	public FeatureDependencies getDependencies() {
		return new FeatureDependencies(fm);
	}

	public boolean isValid() throws TimeoutException {
		Node root = NodeCreator.createNodes(fm.clone());
		return new SatSolver(root, 1000).isSatisfiable();
	}

	/**
	 * checks whether A implies B for the current feature model.
	 * 
	 * in detail the following condition should be checked whether
	 * 
	 * FM => ((A1 and A2 and ... and An) => (B1 and B2 and ... and Bn))
	 * 
	 * is true for all values
	 * 
	 * @param A
	 *            set of features that form a conjunction
	 * @param B
	 *            set of features that form a conjunction
	 * @return
	 * @throws TimeoutException
	 */
	public boolean checkImplies(Set<Feature> a, Set<Feature> b)
			throws TimeoutException {
		if (b.isEmpty())
			return true;

		Node featureModel = NodeCreator.createNodes(fm);

		// B1 and B2 and ... Bn
		Node condition = conjunct(b);
		// (A1 and ... An) => (B1 and ... Bn)
		if (!a.isEmpty())
			condition = new Implies(conjunct(a), condition);
		// FM => (A => B)
		Implies finalFormula = new Implies(featureModel, condition);
		return !new SatSolver(new Not(finalFormula), 1000).isSatisfiable();
	}

	/**
	 * checks some condition against the feature model. use only if you know
	 * what you are doing!
	 * 
	 * @return
	 * @throws TimeoutException
	 */
	public boolean checkCondition(Node condition) {

		Node featureModel = NodeCreator.createNodes(fm);
		// FM => (condition)
		Implies finalFormula = new Implies(featureModel, condition.clone());
		try {
			return !new SatSolver(new Not(finalFormula), 1000).isSatisfiable();
		} catch (TimeoutException e) {
			FMCorePlugin.getDefault().logError(e);
			return false;
		}
	}

	/**
	 * Checks whether the given featureSets are mutually exclusive in the given
	 * context and for the current feature model.
	 * 
	 * In detail it is checked whether FM => (context => (at most one of the
	 * featureSets are present)) is a tautology.
	 * 
	 * Here is an example for a truth table of
	 * "at most one the featureSets are present" for three feature sets A, B and
	 * C:
	 * 
	 * A B C result ------------------------ T T T F T T F F T F T F T F F T F T
	 * T F F T F T F F T T F F F T
	 * 
	 * If you want to check XOR(featureSet_1, ..., featureSet_n) you can call
	 * areMutualExclusive() && !mayBeMissing().
	 * 
	 * @param context
	 *            context in which everything is checked
	 * @param featureSets
	 *            list of feature sets that are checked to be mutually exclusive
	 *            in the given context and for the current feature model
	 * 
	 * @return true, if the feature sets are mutually exclusive || false,
	 *         otherwise
	 * @throws TimeoutException
	 */
	public boolean areMutualExclusive(Set<Feature> context,
			List<Set<Feature>> featureSets) throws TimeoutException {
		if ((featureSets == null) || (featureSets.size() < 2))
			return true;

		Node featureModel = NodeCreator.createNodes(fm);

		ArrayList<Node> conjunctions = new ArrayList<Node>(featureSets.size());
		for (Set<Feature> features : featureSets) {
			if ((features != null) && !features.isEmpty())
				conjunctions.add(conjunct(features));
			else
				// If one feature set is empty (i.e. the code-fragment is always
				// present) than it cannot be
				// mutually exclusive to the other ones.
				return false;
		}

		// We build the conjunctive normal form of the formula to check
		LinkedList<Object> forOr = new LinkedList<Object>();
		LinkedList<Object> allNot = new LinkedList<Object>();
		for (int i = 0; i < conjunctions.size(); ++i) {
			allNot.add(new Not(conjunctions.get(i).clone()));

			LinkedList<Object> forAnd = new LinkedList<Object>();
			for (int j = 0; j < conjunctions.size(); ++j) {
				if (j == i)
					forAnd.add(conjunctions.get(j).clone());
				else
					forAnd.add(new Not(conjunctions.get(j).clone()));
			}
			forOr.add(new And(forAnd));
		}
		forOr.add(new And(allNot));

		Node condition = new Or(forOr);
		if ((context != null) && !context.isEmpty())
			condition = new Implies(conjunct(context), condition);

		Implies finalFormula = new Implies(featureModel, condition);
		return !new SatSolver(new Not(finalFormula), 1000).isSatisfiable();
	}

	/**
	 * Checks whether there exists a set of features that is valid within the
	 * feature model and the given context, so that none of the given feature
	 * sets are present, i.e. evaluate to true.
	 * 
	 * In detail it is checked whether there exists a set F of features so that
	 * eval(FM, F) AND eval(context, F) AND NOT(eval(featureSet_1, F)) AND ...
	 * AND NOT(eval(featureSet_n, F)) is true.
	 * 
	 * If you want to check XOR(featureSet_1, ..., featureSet_n) you can call
	 * areMutualExclusive() && !mayBeMissing().
	 * 
	 * @param context
	 *            context in which everything is checked
	 * @param featureSets
	 *            list of feature sets
	 * 
	 * @return true, if there exists such a set of features, i.e. if the
	 *         code-fragment may be missing || false, otherwise
	 * @throws TimeoutException
	 */
	public boolean mayBeMissing(Set<Feature> context,
			List<Set<Feature>> featureSets) throws TimeoutException {
		if ((featureSets == null) || featureSets.isEmpty())
			return false;

		Node featureModel = NodeCreator.createNodes(fm);
		LinkedList<Object> forAnd = new LinkedList<Object>();

		for (Set<Feature> features : featureSets) {
			if ((features != null) && !features.isEmpty())
				forAnd.add(new Not(conjunct(features)));
			else
				return false;
		}

		Node condition = new And(forAnd);
		if ((context != null) && !context.isEmpty())
			condition = new And(conjunct(context), condition);

		Node finalFormula = new And(featureModel, condition);
		return new SatSolver(finalFormula, 1000).isSatisfiable();
	}

	/**
	 * Checks whether there exists a set of features that is valid within the
	 * feature model, so that all given features are present.
	 * 
	 * In detail it is checked whether there exists a set F of features so that
	 * eval(FM, F) AND eval(feature_1, F) AND eval(feature_n, F) is true.
	 * 
	 * @param features
	 * 
	 * @return true if there exists such a set of features || false, otherwise
	 * @throws TimeoutException
	 */
	public boolean exists(Set<Feature> features) throws TimeoutException {
		if ((features == null) || (features.isEmpty()))
			return true;

		Node featureModel = NodeCreator.createNodes(fm);
		Node finalFormula = new And(featureModel, conjunct(features));
		return new SatSolver(finalFormula, 1000).isSatisfiable();
	}

	public Node conjunct(Set<Feature> b) {
		Iterator<Feature> iterator = b.iterator();
		Node result = new Literal(NodeCreator.getVariable(iterator.next(), fm));
		while (iterator.hasNext())
			result = new And(result, new Literal(NodeCreator.getVariable(
					iterator.next(), fm)));

		return result;
	}

	/**
	 * Returns the list of features that occur in all variants, where one of the
	 * given features is selected. If the given list of features is empty, this
	 * method will calculate the features that are present in all variants
	 * specified by the feature model.
	 * 
	 * @param timeout
	 *            in milliseconds
	 * @param selectedFeatures
	 *            a list of feature names for which
	 * @return a list of features that is common to all variants
	 */
	public LinkedList<String> commonFeatures(long timeout,
			Object... selectedFeatures) {
		Node formula = NodeCreator.createNodes(fm);
		if (selectedFeatures.length > 0)
			formula = new And(formula, new Or(selectedFeatures));
		SatSolver solver = new SatSolver(formula, timeout);
		LinkedList<String> common = new LinkedList<String>();
		for (Literal literal : solver.knownValues())
			if (literal.positive)
				common.add(literal.var.toString());
		return common;
	}

	public LinkedList<Feature> getDeadFeatures() {
		// cloning the FM, because otherwise the resulting formula is wrong if
		// renamed features are involved
		// TODO: Check other calls of createNodes
		Node root = NodeCreator.createNodes(fm.clone());
		LinkedList<Feature> set = new LinkedList<Feature>();
		for (Literal e : new SatSolver(root, 1000).knownValues()) {
			String var = e.var.toString();
			if (!e.positive && !"False".equals(var) && !"True".equals(var)) {
				set.add(fm.getFeature(var));
			}
		}
		return set;
	}
	
	/**
	 * 
	 * @param monitor 
	 * @return Hashmap: key entry is Feature/Constraint, value usually
	 *         indicating the kind of attribute
	 * 
	 *         if Feature
	 */
	public HashMap<Object, Object> analyzeFeatureModel(IProgressMonitor monitor) {
		this.monitor = monitor;
		beginTask("Analyze", fm.getConstraintCount());
		HashMap<Object, Object> oldAttributes = new HashMap<Object, Object>();
		HashMap<Object, Object> changedAttributes = new HashMap<Object, Object>();
		updateFeatures(oldAttributes, changedAttributes);
		if (!cancel) {
			updateConstraints(oldAttributes, changedAttributes);
		}
		// put root always in so it will be refreshed (void/non-void)
		changedAttributes.put(fm.getRoot(), ConstraintAttribute.VOID_MODEL);
		return changedAttributes;
	}

	/**
	 * 
	 * @param name
	 * @param totalWork
	 */
	private void beginTask(String name, int totalWork) {
		if (monitor != null) {
			monitor.beginTask(name, totalWork);
		}
	}

	/**
	 * @param oldAttributes
	 * @param changedAttributes
	 */
	public void updateConstraints(HashMap<Object, Object> oldAttributes,
			HashMap<Object, Object> changedAttributes) {
		FeatureModel clone = fm.clone();
		LinkedList<Feature> fmDeadFeatures = fm.getCalculatedDeadFeatures();
		boolean hasDeadFeatures = !fmDeadFeatures.isEmpty();
		boolean hasFalsOptionalFeatures = !fm.getFalseOptionalFeatures().isEmpty();
		try {
			for (Constraint constraint : new ArrayList<Constraint>(fm.getConstraints())) {
				if (cancel) {
					return;
				}
				setSubTask(constraint.toString());
				worked(1);
				oldAttributes.put(constraint, constraint.getConstraintAttribute());
				constraint.setContainedFeatures(constraint.getNode());
				
				// if the constraint leads to false optionals it is added to
				// changedAttributes in order to refresh graphics later
				if (!hasFalsOptionalFeatures) {
					constraint.getFalseOptional().clear();
				} else if (constraint.setFalseOptionalFeatures()) {
					changedAttributes.put(constraint, ConstraintAttribute.UNSATISFIABLE);
				}
				constraint.setConstraintAttribute(ConstraintAttribute.NORMAL,
						false);
				// tautology
				SatSolver satsolverTAU = new SatSolver(new Not(constraint
						.getNode().clone()), 1000);
				try {
					if (!satsolverTAU.isSatisfiable()) {
						if (oldAttributes.get(constraint) != ConstraintAttribute.TAUTOLOGY) {
							changedAttributes.put(constraint,
									ConstraintAttribute.TAUTOLOGY);
						}
						constraint.setConstraintAttribute(
								ConstraintAttribute.TAUTOLOGY, false);
					}
				} catch (TimeoutException e) {
					FMCorePlugin.getDefault().logError(e);
				}

				if (fm.valid) {
					if (hasDeadFeatures) {
						List<Feature> deadFeatures = constraint.getDeadFeatures(clone, fmDeadFeatures);
						if (!deadFeatures.isEmpty()) {
							constraint.setDeadFeatures(deadFeatures);
							constraint.setConstraintAttribute(ConstraintAttribute.DEAD, false);
							changedAttributes.put(constraint, ConstraintAttribute.DEAD);
						}
					}
					// redundant constraint?
					// TODO for some models this least very long and the solver does not stop after timeout
					// this happens if the model has many abstract features
					findRedundantConstraints(constraint, changedAttributes, oldAttributes);
				}
				// makes feature model void?
				else {
					// inconsistency?
					FeatureModel clonedModel = fm.clone();
					clonedModel.removePropositionalNode(constraint);
					try {
						if (clonedModel.getAnalyser().isValid()) {
							if (oldAttributes.get(constraint) != ConstraintAttribute.VOID_MODEL) {
								changedAttributes.put(constraint,
										ConstraintAttribute.VOID_MODEL);
							}
							constraint.setConstraintAttribute(
									ConstraintAttribute.VOID_MODEL, false);
						}
					} catch (TimeoutException e) {
						FMCorePlugin.getDefault().logError(e);
					}
					// contradiction?
					SatSolver satsolverUS = new SatSolver(constraint.getNode()
							.clone(), 1000);
					try {
						if (!satsolverUS.isSatisfiable()) {
							if (oldAttributes.get(constraint) != ConstraintAttribute.UNSATISFIABLE) {
								changedAttributes.put(constraint,
										ConstraintAttribute.UNSATISFIABLE);

							}
							constraint.setConstraintAttribute(
									ConstraintAttribute.UNSATISFIABLE, false);
						}
					} catch (TimeoutException e) {
						FMCorePlugin.getDefault().logError(e);
					}

				}

				/**
				 * TODO @Jens This is a bad workaround to update some constraints that are changed to NORMAL
				 * 			  Revise this so only changed constraints will be updated(unimportant)
				 */
				if (!changedAttributes.containsKey(constraint)) {
					changedAttributes.put(constraint, ConstraintAttribute.NORMAL);
				}

			}
		} catch (ConcurrentModificationException e) {
			FMCorePlugin.getDefault().logError(e);
		}

	}

	/**
	 * 
	 */
	private void worked(int workDone) {
		if (monitor != null) {
			monitor.worked(workDone);
		}
	}
	
	private void setSubTask(String name) {
		if (monitor != null) {
			monitor.subTask(name);
		}
	}

	/**
	 * @param constraint 
	 * @param changedAttributes 
	 * @param oldAttributes 
	 * 
	 */
	public void findRedundantConstraints(Constraint constraint, HashMap<Object, Object> changedAttributes, HashMap<Object,Object> oldAttributes) {
		FeatureModel dirtyModel = fm.clone();
		dirtyModel.removePropositionalNode(constraint.getNode());
		ModelComparator comparator = new ModelComparator(500);
		Comparison comparison = comparator.compare(fm, dirtyModel);
		if (comparison == Comparison.REFACTORING) {
			if (oldAttributes.get(constraint) != ConstraintAttribute.REDUNDANT) {
				changedAttributes.put(constraint, ConstraintAttribute.REDUNDANT);
			}
			constraint.setConstraintAttribute(ConstraintAttribute.REDUNDANT, false);
		}
	}

	/**
	 * @param oldAttributes
	 * @param changedAttributes
	 */
	public void updateFeatures(HashMap<Object, Object> oldAttributes,
			HashMap<Object, Object> changedAttributes) {
		for (Feature bone : fm.getFeatures()) {
			oldAttributes.put(bone, bone.getFeatureStatus());
			if (bone.getFeatureStatus() != FeatureStatus.NORMAL)
				changedAttributes.put(bone, FeatureStatus.FALSE_OPTIONAL);
			bone.setFeatureStatus(FeatureStatus.NORMAL, false);
			bone.setRelevantConstraints();
		}

		try {
			fm.valid = isValid();
		} catch (TimeoutException e) {
			fm.valid = true;
			FMCorePlugin.getDefault().logError(e);
		}

		try {
			LinkedList<Feature> deadFeatures = fm.getCalculatedDeadFeatures();
			for (Feature deadFeature : getDeadFeatures()) {
				if (cancel) {
					return;
				}
				if (deadFeature != null) {
					if (oldAttributes.get(deadFeature) != FeatureStatus.DEAD) {
						changedAttributes.put(deadFeature, FeatureStatus.DEAD);
					}
					deadFeatures.add(deadFeature);
					deadFeature.setFeatureStatus(FeatureStatus.DEAD, false);

				}
			}
		} catch (Exception e) {
			FMCorePlugin.getDefault().logError(e);
		}

		try {
			if (fm.valid) {
				getFalseOptionalFeature(oldAttributes, changedAttributes);
			}
		} catch (Exception e) {
			FMCorePlugin.getDefault().logError(e);
		}
	}

	/**
	 * @param oldAttributes
	 * @param changedAttributes
	 */
	private void getFalseOptionalFeature(HashMap<Object, Object> oldAttributes,
			HashMap<Object, Object> changedAttributes) {
		LinkedList<Feature> falseOptionalFeatures = fm.getFalseOptionalFeatures();
		falseOptionalFeatures.clear();
		for (Feature f : getFalseOptionalFeatures()) {
			changedAttributes.put(f,FeatureStatus.FALSE_OPTIONAL);
			f.setFeatureStatus(FeatureStatus.FALSE_OPTIONAL, false);
			falseOptionalFeatures.add(f);
		}
	}
	
	public LinkedList<Feature> getFalseOptionalFeatures() {
		// TODO Thomas: improve calculation effort and
		// correct calculation (is this feature always selected given
		// that the parent feature is selected)
		LinkedList<Feature> falseOptionalFeatures = new LinkedList<Feature>();
		for (Feature feature : fm.getFeatures()) {
			try {
				if (!feature.isMandatory() && !feature.isRoot()) {
					SatSolver satsolver = new SatSolver(new Not(new Implies(
							new And(new Literal(feature.getParent().getName()),
									NodeCreator.createNodes(fm.clone())),
							new Literal(feature.getName()))), 1000);
					if (!satsolver.isSatisfiable()) {
						falseOptionalFeatures.add(feature);
					}
				}
			} catch (TimeoutException e) {
				FMCorePlugin.getDefault().logError(e);
			}
		}
		return falseOptionalFeatures;
	}

	public int countConcreteFeatures() {
		int number = 0;
		for (Feature feature : fm.getFeatures())
			if (feature.isConcrete())
				number++;
		return number;
	}

	public int countHiddenFeatures() {
		int number = 0;
		for (Feature feature : fm.getFeatures()) {
			if (feature.isHidden() || feature.hasHiddenParent()) {
				number++;
			}
		}
		return number;
	}

	public int countTerminalFeatures() {
		int number = 0;
		for (Feature feature : fm.getFeatures())
			if (!feature.hasChildren())
				number++;
		return number;
	}
	
	/**
	 * Sets the cancel status of analysis.<br>
	 * <code>true</code> if analysis should be stopped.
	 */
	public void cancel(boolean value) {
		cancel = value;
	}
}