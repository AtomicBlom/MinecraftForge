package net.minecraftforge.common.plon;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import net.minecraftforge.common.util.DisjointMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.Map;

import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/31/16.
 */
public class Unifier
{
    //private final DisjointMap<ISExp> union = new DisjointMap<ISExp>(ops);
    // type vars are globally unique so this should be good
    // TODO: check
    //private final java.util.Map<Symbol, ISExp> boundVars = Maps.newHashMap();
    //private final Multimap<ISExp, ISExp> constraints = HashMultimap.create();
    private int nextVar = 0;
    private int nextRow = 0;
    private int nextLab = 0;

    ISExp getFreshFrom(Symbol S)
    {
        switch(S.value.charAt(0))
        {
            case 'V': return getFreshVar();
            case 'R': return getFreshVar();
            case 'L': return getFreshVar();
        }
        throw new IllegalStateException("getFreshFrom with unknown structure: " + S);
    }

    private ISExp getFresh(char c)
    {
        ISExp symbol = makeSymbol(("" + c + nextVar++).intern());
        globalEnv.substitution.makeSet((Symbol) symbol);
        return symbol;
    }

    ISExp getFreshVar()
    {
        return getFresh('V');
    }

    ISExp getFreshRow()
    {
        return getFresh('R');
    }

    ISExp getFreshLab()
    {
        return getFresh('L');
    }

    private Environment globalEnv = new Environment();

    /*ISExp find(ISExp exp)
    {
        return globalEnv.find(exp);
    }*/

    void unify(ISExp first, ISExp second)
    {
        Set<Environment> ret = Sets.newHashSet();
        EnvSet envSet = unify(first, second, new EnvSet(globalEnv));
        UnifyException exception = null;
        for (Environment env : envSet.evs)
        {
            try
            {
                env.checkLacks();
                ret.add(env);
            }
            catch (UnifyException e)
            {
                exception = e;
            }
        }
        EnvSet newEnvSet = new EnvSet(ImmutableSet.copyOf(ret), exception);
        if(newEnvSet.evs.size() == 1)
        {
            EnvSet oldNewEnvSet;
            do
            {
                oldNewEnvSet = newEnvSet;
                newEnvSet = improve(newEnvSet.evs.iterator().next());
            }
            while(!oldNewEnvSet.evs.equals(newEnvSet.evs) && newEnvSet.evs.size() == 1);
        }
        if(newEnvSet.evs.size() == 1)
        {
            globalEnv = newEnvSet.evs.iterator().next();
        }
        else if(newEnvSet.evs.size() == 0)
        {
            exception = newEnvSet.exception;
            if(exception == null)
            {
                exception = envSet.exception;
            }
            throw new IllegalStateException("Couldn't unify: " + typeToString(first) + " and " + typeToString(second), exception);
        }
        else
        {
            //throw new IllegalStateException("Couldn't unify uniquely: " + typeToString(first) + " and " + typeToString(second));
            // shrug
            // FIXME: at least print a warning here
            globalEnv = newEnvSet.evs.iterator().next();
        }
    }

    void addLacks(ISExp r, ISExp label)
    {
        Multimap<ISExp, ISExp> lacks = HashMultimap.create(globalEnv.lacks);
        lacks.put(r, label);
        globalEnv = new Environment(globalEnv.substitution, globalEnv.equals, ImmutableMultimap.copyOf(lacks));
    }

    private static final class UnionException extends RuntimeException
    {
        private final ISExp first;
        private final ISExp second;

        private UnionException(ISExp first, ISExp second)
        {
            super("Couldn't unify " + first + " and " + second);
            this.first = first;
            this.second = second;
        }
    }

    private enum UniqueOptionalValueOps implements DisjointMap.IValueOps<ISExp, Optional<ISExp>>
    {
        INSTANCE;

        @Override
        public Optional<ISExp> makeValue(ISExp key)
        {
            return Optional.absent();
        }

        @Override
        public Optional<ISExp> unionValues(Optional<ISExp> v1, Optional<ISExp> v2)
        {
            if(v1.isPresent())
            {
                if(v2.isPresent())
                {
                    ISExp val1 = v1.get();
                    ISExp val2 = v2.get();
                    if(!val1.equals(val2))
                    {
                        throw new UnionException(val1, val2);
                    }
                }
                return v1;
            }
            return v2;
        }

        @Override
        // Hmm
        public Optional<ISExp> copyValue(Optional<ISExp> v)
        {
            return v;
        }
    }

    private enum DummyOps implements DisjointMap.IValueOps<ISExp, Void>
    {
        INSTANCE;

        @Override
        public Void makeValue(ISExp isExp)
        {
            return null;
        }

        @Override
        public Void unionValues(Void v1, Void v2)
        {
            return null;
        }

        @Override
        public Void copyValue(Void aVoid)
        {
            return null;
        }
    }

    private static void putSubs(DisjointMap<Symbol, Optional<ISExp>> subs, ImmutableMap<Symbol, ISExp> newSubs)
    {
        for (Map.Entry<Symbol, ISExp> entry : newSubs.entrySet())
        {
            subs.makeSet(entry.getKey());
            if(entry.getValue() instanceof Symbol)
            {
                Symbol value = (Symbol) entry.getValue();
                subs.makeSet(value);
                subs.union(entry.getKey(), value);
            }
            else
            {
                subs.set(entry.getKey(), Optional.of(entry.getValue()));
            }
        }
    }

    private void addBoundInValue(ISExp type, Set<ISExp> boundInValues, Environment env)
    {
        type = env.find(type);
        if(type instanceof Symbol)
        {
            boundInValues.add(type);
        }
        else if(type instanceof IList)
        {
            // don't look inside labels, there's no types there
            if (type instanceof Cons && ((Cons) type).car.equals(makeString("label")))
            {
                return;
            }
            IList list = (IList) type;
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                addBoundInValue(cons.car, boundInValues, env);
                list = cons.cdr;
            }
        }
        else if(type instanceof net.minecraftforge.common.plon.AST.Map)
        {
            for(Map.Entry<? extends ISExp, ? extends ISExp> entry : ((net.minecraftforge.common.plon.AST.Map) type).value.entrySet())
            {
                addBoundInValue(entry.getKey(), boundInValues, env);
                addBoundInValue(entry.getValue(), boundInValues, env);
            }
        }
    }

    ISExp reify(ISExp type, java.util.Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> boundVarTypes)
    {
        globalEnv = globalEnv.applySubstitution();
        SubVisitor subVisitor = new SubVisitor(globalEnv.substitution);
        type = visitType(type, subVisitor);
        //Set<ISExp> freeRootBuilder = Sets.newHashSet();
        Set<ISExp> boundInValues = Sets.newHashSet();
        /*for (Map.Entry<Symbol, Optional<ISExp>> entry : globalEnv.substitution)
        {
            Symbol symbol = globalEnv.substitution.find(entry.getKey()).get();
            if(entry.getValue().isPresent())
            {
                boundInValues.add(symbol);
            }
        }*/
        for(ISExp bound : boundVarTypes)
        {
            // FIXME: breaks the select example
            addBoundInValue(bound, boundInValues, globalEnv);
        }
        boundInValues.addAll(boundVarTypes);
        /*freeRootBuilder.removeAll(boundInValues);
        freeRootBuilder.removeAll(boundVarTypes);
        ImmutableSet<ISExp> freeRoots = ImmutableSet.copyOf(freeRootBuilder);*/
        ReifyVisitor visitor = new ReifyVisitor(newVars, ImmutableSet.copyOf(boundInValues));
        GatheringVisitor gVisitor = new GatheringVisitor();
        visitType(type, gVisitor);
        ISExp newType = visitType(type, visitor);
        Multimap<ISExp, ISExp> builder = HashMultimap.create();
        builder.putAll(globalEnv.lacks);
        Set<Symbol> newUsed = Sets.newHashSet(gVisitor.used);
        ImmutableList<Map.Entry<ISExp, Void>> oldEq = ImmutableList.copyOf(globalEnv.equals);
        boolean changed;
        do
        {
            changed = false;
            for (Map.Entry<ISExp, ISExp> entry : globalEnv.lacks.entries())
            {
                gVisitor.used.clear();
                visitType(entry.getKey(), gVisitor);
                visitType(entry.getValue(), gVisitor);
                if (Sets.intersection(gVisitor.used, newUsed).isEmpty())
                {
                    builder.put(entry.getKey(), entry.getValue());
                }
                else
                {
                    if(!Sets.difference(gVisitor.used, newUsed).isEmpty())
                    {
                        changed = true;
                        newUsed.addAll(gVisitor.used);
                    }
                    ISExp key = visitType(entry.getKey(), visitor);
                    ISExp value = visitType(entry.getValue(), visitor);
                    builder.put(key, value);
                }
            }

            globalEnv = new Environment(globalEnv.substitution, globalEnv.equals, ImmutableMultimap.copyOf(builder));

            for (Map.Entry<ISExp, Void> entry : oldEq)
            {
                ISExp root = globalEnv.equals.find(entry.getKey()).get();
                if (entry.getKey().equals(root))
                {
                    continue;
                }

                gVisitor.used.clear();
                visitType(entry.getKey(), gVisitor);
                visitType(root, gVisitor);
                if (!Sets.intersection(gVisitor.used, newUsed).isEmpty())
                {
                    if(!Sets.difference(gVisitor.used, newUsed).isEmpty())
                    {
                        changed = true;
                        newUsed.addAll(gVisitor.used);
                    }
                    ISExp e2 = visitType(root, visitor);
                    // optimization
                    if(entry.getKey() instanceof Symbol && e2.equals(root) && !newVars.containsKey(entry.getKey()))
                    {
                        continue;
                    }
                    ISExp e1 = visitType(entry.getKey(), visitor);
                    if (!e1.equals(entry.getKey()) || !e2.equals(root))
                    {
                        // globalEnv = unifyRows(e1, e2, globalEnv);
                        unify(prodType(e1), prodType(e2));
                    }
                }
            }
        }
        while(changed);
        return newType;
    }

    ImmutableSet<Symbol> substFree()
    {
        return globalEnv.substFree();
    }

    private class ReifyVisitor implements Function<Symbol, ISExp>
    {
        private final Map<ISExp, ISExp> newVars;
        private final ImmutableSet<ISExp> boundVarTypes;

        private ReifyVisitor(Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> boundVarTypes)
        {
            this.newVars = newVars;
            this.boundVarTypes = boundVarTypes;
        }

        @Override
        public ISExp apply(Symbol type)
        {
            if(!boundVarTypes.contains(type))
            {
                if (!newVars.containsKey(type))
                {
                    ISExp newVar = getFreshFrom(type);
                    newVars.put(type, newVar);
                }
            }
            if(newVars.containsKey(type))
            {
                return newVars.get(type);
            }
            return type;
        }
    }

    private static ISExp visitType(ISExp type, Function<Symbol, ISExp> symbolVisitor)
    {
        //type = env.find(type); // Hmm
        if(type instanceof Symbol)
        {
            return symbolVisitor.apply((Symbol) type);
        }
        if(type instanceof IList)
        {
            // don't look inside labels, there's no types there
            if(type instanceof Cons && ((Cons) type).car.equals(makeString("label")))
            {
                return type;
            }
            return visitTypeList((IList) type, symbolVisitor);
        }
        if(type instanceof net.minecraftforge.common.plon.AST.Map)
        {
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            for(Map.Entry<? extends ISExp, ? extends ISExp> entry : ((net.minecraftforge.common.plon.AST.Map) type).value.entrySet())
            {
                builder.put(
                visitType(entry.getKey(), symbolVisitor),
                visitType(entry.getValue(), symbolVisitor)
                );
            }
        }
        else if(type == MNil.INSTANCE || type == Nil.INSTANCE || type instanceof StringAtom)
        {
            return type;
        }
        throw new IllegalStateException("Not a well-formed type: " + type);
    }

    private static IList visitTypeList(IList args, Function<Symbol, ISExp> symbolVisitor)
    {
        if(args == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) args;
        return new Cons(visitType(cons.car, symbolVisitor), visitTypeList(cons.cdr, symbolVisitor));
    }

    private static class UnifyException extends Exception
    {
        public UnifyException(String message)
        {
            super(message);
        }
    }

    private static class GatheringVisitor implements Function<Symbol, ISExp>
    {
        private final Set<Symbol> used = Sets.newHashSet();

        @Override
        public ISExp apply(Symbol type)
        {
            used.add(type);
            return type;
        }
    }

    private static class UsedVisitor implements Function<Symbol, ISExp>
    {
        private final Set<Symbol> used;
        private boolean usedType = false;

        private UsedVisitor(Set<Symbol> used)
        {
            this.used = used;
        }

        @Override
        public ISExp apply(Symbol type)
        {
            usedType |= used.contains(type);
            return type;
        }
    }

    private static class SubVisitor implements Function<Symbol, ISExp>
    {
        private final DisjointMap<Symbol, Optional<ISExp>> subs;
        private Symbol r;

        private SubVisitor(DisjointMap<Symbol, Optional<ISExp>> subs)
        {
            this.subs = subs;
        }

        @Override
        public ISExp apply(Symbol type)
        {
            Optional<Symbol> newType = subs.find(type);
            if(newType.isPresent())
            {
                Optional<Optional<ISExp>> newValue = subs.get(type);
                ISExp value = type;
                if(newValue.isPresent() && newValue.get().isPresent())
                {
                    value = newValue.get().get();
                }
                else
                {
                    value = newType.get();
                }
                if(!type.equals(value) && !type.equals(r) && !(value.equals(r)))
                {
                    return visitType(value, this);
                }
            }
            return type;
        }
    }

    private static final class Environment
    {
        private final DisjointMap<Symbol, Optional<ISExp>> substitution; // row ~ row is actually here too
        private final DisjointMap<ISExp, Void> equals; // row ~ row
        private final ImmutableMultimap<ISExp, ISExp> lacks; // row -> label

        public Environment()
        {
            this.substitution = DisjointMap.create(UniqueOptionalValueOps.INSTANCE);
            this.equals = DisjointMap.create(DummyOps.INSTANCE);
            this.lacks = ImmutableMultimap.<ISExp, ISExp>of();
        }

        private Environment(DisjointMap<Symbol, Optional<ISExp>> substitution, DisjointMap<ISExp, Void> equals, ImmutableMultimap<ISExp, ISExp> lacks)
        {
            this.substitution = substitution;
            this.equals = equals;
            this.lacks = lacks;
        }

        private Environment(ImmutableMap<Symbol, ISExp> substitution, ImmutableMap<ISExp, ISExp> equals, ImmutableSet<Pair<ISExp, ISExp>> lacks)
        {
            this.substitution = DisjointMap.create(UniqueOptionalValueOps.INSTANCE);
            putSubs(this.substitution, substitution);
            this.equals = DisjointMap.create(DummyOps.INSTANCE);
            for (Map.Entry<ISExp, ISExp> entry : equals.entrySet())
            {
                this.equals.makeSet(entry.getKey());
                this.equals.makeSet(entry.getValue());
                this.equals.union(entry.getKey(), entry.getValue());
            }
            ImmutableMultimap.Builder<ISExp, ISExp> builder = ImmutableMultimap.builder();
            for (Pair<ISExp, ISExp> pair : lacks)
            {
                builder.put(pair.getLeft(), pair.getRight());
            }
            this.lacks = builder.build();
        }

        private static Environment fromSub(Symbol from, ISExp to)
        {
            return new Environment(ImmutableMap.of(from, to), ImmutableMap.<ISExp, ISExp>of(), ImmutableSet.<Pair<ISExp, ISExp>>of());
        }

        private static Environment fromEq(ISExp from, ISExp to)
        {
            return new Environment(ImmutableMap.<Symbol, ISExp>of(), ImmutableMap.of(from, to), ImmutableSet.<Pair<ISExp, ISExp>>of());
        }

        Environment mergeWith(Environment env) throws UnifyException
        {
            DisjointMap<Symbol, Optional<ISExp>> newS = substitution.copy();
            for (Map.Entry<Symbol, Symbol> entry : env.substitution.pairs())
            {
                newS.makeSet(entry.getKey());
                newS.makeSet(entry.getValue());
                try
                {
                    newS.union(entry.getKey(), entry.getValue());
                    newS.set(entry.getKey(), env.substitution.get(entry.getKey()).get());
                }
                catch (UnionException e)
                {
                    throw new UnifyException("Couldn't make equivalent: " + typeToString(entry.getKey(), env) + " and " + typeToString(entry.getValue(), this));
                }
            }
            DisjointMap<ISExp, Void> newE = equals.copy();
            for (Map.Entry<ISExp, ISExp> entry : env.equals.pairs())
            {
                newE.makeSet(entry.getKey());
                newE.makeSet(entry.getValue());
                if(entry.getKey().equals(entry.getValue()))
                {
                    continue;
                }
                try
                {
                    newE.union(entry.getKey(), entry.getValue());
                    // value is null, not setting explicitly
                }
                catch (UnionException e)
                {
                    // FIXME
                    throw new UnifyException("Couldn't make equivalent: " + typeToString(entry.getKey(), env) + " and " + typeToString(entry.getValue(), this));
                }
            }
            Multimap<ISExp, ISExp> newLacks = HashMultimap.create();
            newLacks.putAll(lacks);
            newLacks.putAll(env.lacks);
            Environment newEnv = new Environment(newS, newE, ImmutableMultimap.copyOf(newLacks));
            newEnv.checkLacks();
            return newEnv;
        }

        void checkLacks() throws UnifyException
        {
            for (Map.Entry<ISExp, ISExp> entry : lacks.entries())
            {
                checkLack(entry.getKey(), entry.getValue());
            }
        }

        void checkLack(ISExp row, ISExp label) throws UnifyException
        {
            row = find(row);
            if (row instanceof Cons)
            {
                if (length(row) == 1)
                {
                    // empty row lack everything
                    return;
                }
                Cons c1 = (Cons) row;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;
                ISExp l2 = find(c2.car);
                if(label.equals(l2))
                {
                    throw new UnifyException("Unsatisfiable constraint: " + typeToString(row, this) + " lacks " + label);
                }
                checkLack(c4.car, label);
            }
            // otherwise can't check the constraint at this time
        }

        Environment trimUselessPredicates()
        {
            GatheringVisitor visitor = new GatheringVisitor();
            for (Map.Entry<Symbol, Optional<ISExp>> entry : substitution)
            {
                visitType(entry.getKey(), visitor);
                if(entry.getValue().isPresent())
                {
                    visitType(entry.getValue().get(), visitor);
                }
            }

            UsedVisitor usedVisitor = new UsedVisitor(visitor.used);

            DisjointMap<ISExp, Void> newEq = DisjointMap.create(DummyOps.INSTANCE);

            for (Map.Entry<ISExp, Void> entry : equals)
            {
                ISExp r1 = entry.getKey();
                ISExp r2 = equals.find(r1).get();
                usedVisitor.usedType = false;
                visitType(r1, usedVisitor);
                boolean r1Used = usedVisitor.usedType;
                usedVisitor.usedType = false;
                visitType(r2, usedVisitor);
                boolean r2Used = usedVisitor.usedType;
                if(r1Used && r2Used)
                {
                    newEq.makeSet(r1);
                    newEq.makeSet(r2);
                    newEq.union(r1, r2);
                }
            }

            Multimap<ISExp, ISExp> newLacks = HashMultimap.create();

            for (Map.Entry<ISExp, ISExp> entry : lacks.entries())
            {
                usedVisitor.usedType = false;
                visitType(entry.getKey(), usedVisitor);
                boolean rUsed = usedVisitor.usedType;
                usedVisitor.usedType = false;
                visitType(entry.getValue(), usedVisitor);
                boolean lUsed = usedVisitor.usedType;
                if(rUsed && lUsed)
                {
                    newLacks.put(entry.getKey(), entry.getValue());
                }
            }

            return new Environment(substitution.copy(), newEq, ImmutableMultimap.copyOf(newLacks));
        }

        Environment applySubstitution()
        {
            DisjointMap<ISExp, Void> newEq = DisjointMap.create(DummyOps.INSTANCE);

            SubVisitor visitor = new SubVisitor(substitution);

            for (Map.Entry<ISExp, Void> entry : equals)
            {
                ISExp r1 = entry.getKey();
                ISExp r2 = equals.find(r1).get();
                if(!r1.equals(r2))
                {
                    if(r1 instanceof Symbol)
                    {
                        visitor.r = (Symbol) r1;
                    }
                    else if(r2 instanceof Symbol)
                    {
                        visitor.r = (Symbol) r2;
                    }
                    ISExp newR1 = visitType(r1, visitor);
                    ISExp newR2 = visitType(r2, visitor);
                    newR1 = sortRow(newR1);
                    newR2 = sortRow(newR2);
                    newEq.makeSet(newR1);
                    newEq.makeSet(newR2);
                    newEq.union(newR1, newR2);
                }
            }

            visitor.r = null;
            Multimap<ISExp, ISExp> builder = HashMultimap.create();

            for (Map.Entry<ISExp, ISExp> entry : lacks.entries())
            {
                ISExp r = visitType(entry.getKey(), visitor);
                ISExp l = visitType(entry.getValue(), visitor);
                builder.put(r, l);
            }

            ImmutableMultimap<ISExp, ISExp> newLacks = ImmutableMultimap.copyOf(builder);
            if(newEq.equals(equals) && newLacks.equals(lacks))
            {
                return this;
            }
            return new Environment(substitution.copy(), newEq, newLacks);
        }

        private ImmutableSet<Symbol> substFree()
        {
            Set<Symbol> bound = Sets.newHashSet();
            Set<Symbol> used = Sets.newHashSet();

            UsedVisitor visitor = new UsedVisitor(used);

            for (Map.Entry<Symbol, Optional<ISExp>> entry : substitution)
            {
                visitType(entry.getKey(), visitor);
                if(entry.getValue().isPresent())
                {
                    visitType(entry.getValue().get(), visitor);
                }
                Symbol s1 = entry.getKey();
                Symbol s2 = substitution.find(s1).get();
                if(!s1.equals(s2))
                {
                    bound.add(s1);
                }
            }

            /*for (Map.Entry<ISExp, Void> entry : equals)
            {
                ISExp r1 = entry.getKey();
                ISExp r2 = equals.find(r1).get();
                visitType(r1, visitor);
                visitType(r2, visitor);
            }

            for (Map.Entry<ISExp, ISExp> entry : lacks.entries())
            {
                visitType(entry.getKey(), visitor);
                visitType(entry.getValue(), visitor);
            }*/

            return Sets.difference(used, bound).immutableCopy();
        }

        public ISExp find(ISExp exp)
        {
            if(exp instanceof Symbol)
            {
                Symbol var = (Symbol) exp;
                Optional<Symbol> root = substitution.find(var);
                if(root.isPresent())
                {
                    var = root.get();
                }
                Optional<Optional<ISExp>> value = substitution.get(var);
                if (value.isPresent() && value.get().isPresent())
                {
                    return value.get().get();
                }
                return var;
            }
            return exp;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Environment that = (Environment) o;
            return com.google.common.base.Objects.equal(substitution, that.substitution) &&
            Objects.equal(equals, that.equals) &&
            Objects.equal(lacks, that.lacks);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(substitution, equals, lacks);
        }

        public boolean equal(ISExp r1, ISExp r2)
        {
            r1 = find(r1);
            r2 = find(r2);
            if(r1.equals(r2))
            {
                return true;
            }
            if(r1 instanceof Cons && r2 instanceof Cons)
            {
                Cons c1 = (Cons) r1;
                Cons c2 = (Cons) r2;
                return equal(c1.car, c2.car) && equal(c1.cdr, c2.cdr);
            }
            return false;
        }
    }

    private static class EnvSet
    {
        private final ImmutableSet<Environment> evs;
        private final UnifyException exception;

        public EnvSet(Environment env)
        {
            this(ImmutableSet.of(env));
        }

        public EnvSet(ImmutableSet<Environment> evs, UnifyException exception)
        {
            this.evs = evs;
            this.exception = exception;
        }

        public EnvSet(ImmutableSet<Environment> evs)
        {
            this(evs, null);
        }

        public EnvSet(UnifyException exception)
        {
            this(ImmutableSet.<Environment>of(), exception);
        }

        public EnvSet mergeWithEnv(final Environment env) throws UnifyException
        {
            Set<Environment> set = Sets.newHashSet();
            for (Environment ev : evs)
            {
                set.add(ev.mergeWith(env));
            }
            return new EnvSet(ImmutableSet.copyOf(set));
        }
    }

    // flatMap
    private EnvSet unify(final ISExp first, final ISExp second, EnvSet evs)
    {
        ImmutableSet.Builder<Environment> builder = ImmutableSet.builder();
        UnifyException exception = null;
        for (Environment env : evs.evs)
        {
            try
            {
                EnvSet envSet = unify(first, second, env);
                if(envSet.evs.isEmpty())
                {
                    exception = envSet.exception;
                }
                else for(Environment newEnv : envSet.evs)
                {
                    builder.add(newEnv);
                }
            }
            catch (UnifyException e)
            {
                exception = e;
            }
        }
        ImmutableSet<Environment> set = builder.build();
        return new EnvSet(set, exception);
    }

    private static EnvSet unifyCheck(ISExp first, ISExp second, Environment env)
    {
        try
        {
            return unify(first, second, env);
        }
        catch (UnifyException e)
        {
            return new EnvSet(e);
        }
    }

    private static EnvSet unify(ISExp first, ISExp second, Environment env) throws UnifyException
    {
        final ISExp firstLink = env.find(first);
        final ISExp secondLink = env.find(second);
        if (firstLink instanceof Symbol)
        {
            return union((Symbol) firstLink, secondLink, env);
        }
        else if (secondLink instanceof Symbol)
        {
            return union((Symbol) secondLink, firstLink, env);
        }
        /*else if(firstLink instanceof Cons && length(firstLink) == 3 && secondLink instanceof IMap)
        {
            Cons c1 = (Cons) firstLink;
            Cons c2 = (Cons) c1.cdr;
            Cons c3 = (Cons) c2.cdr;
            if(c1.car.equals(makeString("->")))
            {
                ISExp l = getFreshVar();
                unify(c3.car, PrimTypes.String.type);
                unify(c2.car, new Map())
                return;
            }
        }*/
        // type constructor
        else if (firstLink instanceof Cons && secondLink instanceof Cons)
        {
            Cons firstCons = (Cons) firstLink;
            Cons secondCons = (Cons) secondLink;
            if (firstCons.car instanceof StringAtom && firstCons.car.equals(secondCons.car))
            {
                String name = ((StringAtom) firstCons.car).value;
                if (isRow(firstCons))
                {
                    throw new IllegalStateException("unify called on a row.");
                    // record unification
                    // {} == {}
                    /*if (length(firstCons.cdr) == 0 && length(secondCons.cdr) == 0)
                    {
                        return new EnvSet(env);
                    }
                    if (length(firstCons) == 4)
                    {
                        return new EnvSet(env.mergeWith(Environment.fromEq(firstLink, secondLink)));
                        /*Cons c1 = (Cons) firstCons.cdr;
                        Cons c2 = (Cons) c1.cdr;
                        Cons c3 = (Cons) c2.cdr;
                        ISExp lLink = env.find(c1.car);
                        return unifyRows(lLink, c2.car, c3.car, secondLink, env);*/
                        /*if(!(lLink instanceof Symbol))
                        {
                            ISExp r2 = insert(lLink, c2.car, secondLink);
                            unify(c3.car, r2);
                            return;
                        }* /
                    }
                    /*if(length(secondCons) == 4)
                    {
                        Cons c1 = (Cons) secondCons.cdr;
                        Cons c2 = (Cons) c1.cdr;
                        Cons c3 = (Cons) c2.cdr;
                        ISExp lLink = find(c1.car);
                        if (!(lLink instanceof Symbol))
                        {
                            ISExp r2 = insert(lLink, c2.car, firstLink);
                            unify(c3.car, r2);
                            return;
                        }
                    }*/
                }
                else if (name.equals("label"))
                {
                    if (firstCons.cdr.equals(secondCons.cdr))
                    {
                        return new EnvSet(env);
                    }
                }
                else if(name.equals("*") || name.equals("+"))
                {
                    if(length(firstCons) == 2 && length(secondCons) == 2 && firstCons.car.equals(secondCons.car))
                    {
                        ISExp r1 = ((Cons) firstCons.cdr).car;
                        ISExp r2 = ((Cons) secondCons.cdr).car;
                        return new EnvSet(env.mergeWith(Environment.fromEq(r1, r2)));
                    }
                    throw new UnifyException("Type error: can't product/sum: " + typeToString(firstLink, env) + " and " + typeToString(secondLink, env));
                }
                else // normal constructor
                {
                    IList firstArgs = firstCons.cdr;
                    IList secondArgs = secondCons.cdr;
                    ImmutableSet<Environment> ret = ImmutableSet.of(env);
                    UnifyException exception = null;
                    while (firstArgs != Nil.INSTANCE && secondArgs != Nil.INSTANCE)
                    {
                        Cons c1 = (Cons) firstArgs;
                        Cons c2 = (Cons) secondArgs;
                        ImmutableSet<Environment> arg = unify(c1.car, c2.car, env).evs;
                        ImmutableSet.Builder<Environment> builder = ImmutableSet.builder();
                        for (List<Environment> pair : Sets.cartesianProduct(ImmutableList.of(ret, arg)))
                        {
                            try
                            {
                                builder.add(pair.get(0).mergeWith(pair.get(1)));
                            }
                            catch(UnifyException e) // couldn't merge
                            {
                                exception = e;
                            }
                        }
                        ret = builder.build();
                        firstArgs = c1.cdr;
                        secondArgs = c2.cdr;
                    }
                    if (firstArgs == secondArgs) // == Nil.INSTANCE
                    {
                        return new EnvSet(ret, exception);
                    }
                    throw new UnifyException("Argument lists have different sizes: " + length(firstCons.cdr) + " and " + length(secondCons.cdr) + ", types: " + typeToString(firstLink, env) + " and " + typeToString(secondLink, env));
                }
            }
        }
        else if (firstLink.equals(secondLink))
        {
            return new EnvSet(env);
        }
        throw new UnifyException("Type error: can't unify " + typeToString(firstLink, env) + " and " + typeToString(secondLink, env));
    }

    private EnvSet improve(Environment env)
    {
        ImmutableSet<Environment> envSet = ImmutableSet.of(env);
        UnifyException exception = null;
        //for(int i = 0; i < 3; i++)
        {
            for (Map.Entry<ISExp, ISExp> entry : env.equals.pairs())
            {
                ISExp r1 = entry.getKey();
                ISExp r2 = entry.getValue();
                if (r1.equals(r2))
                {
                    continue;
                }
                Set<Environment> set = Sets.newHashSet();
                for (Environment e : envSet)
                {
                    EnvSet newEvs = unifyRows(r1, r2, e, DefaultUnifier.INSTANCE);
                    if (newEvs.evs.isEmpty())
                    {
                        exception = newEvs.exception;
                    }
                    for (Environment ev : newEvs.evs)
                    {
                        try
                        {
                            ev.checkLacks();
                            //ev = ev.trimUselessPredicates();
                            set.add(ev);
                        } catch (UnifyException e1)
                        {
                            exception = e1;
                        }
                    }
                }
                envSet = ImmutableSet.copyOf(set);
            }
        }
        return new EnvSet(envSet, exception);
    }

    private EnvSet unifyRows(ISExp r1, ISExp r2, Environment env, IRowTypeUnifier rowUnifier)
    {
        r1 = env.find(r1);
        r2 = env.find(r2);
        if(env.equal(r1, r2))
        {
            return new EnvSet(env);
        }
        if (r1 instanceof Symbol && r2 instanceof Symbol)
        {
            try
            {
                return union((Symbol) r1, r2, env);
            }
            catch (UnifyException e1)
            {
                ISExp first = env.find(r1);
                ISExp second = env.find(r2);
                // FIXME: recursion?
                return unifyRows(first, second, env, rowUnifier);
            }
        }
        else
        {
            if(r1 instanceof Symbol)
            {
                ISExp t = r1;
                r1 = r2;
                r2 = t;
            }
            Cons c0 = (Cons) r1;
            if(c0.cdr == Nil.INSTANCE) // empty row
            {
                if(r1.equals(r2))
                {
                    return new EnvSet(env);
                }
                if(r2 instanceof Symbol)
                {
                    try
                    {
                        return union((Symbol) r2, r1, env);
                    }
                    catch (UnifyException e)
                    {
                        return new EnvSet(e);
                    }
                }
                if(r2 instanceof Cons)
                {
                    Cons c1 = (Cons) r2;
                    if(length(c1) == 3) // from or to
                    {
                        Cons c2 = (Cons) c1.cdr;
                        Cons c3 = (Cons) c2.cdr;
                        // unify to.r or from.r with empty row
                        return unifyRows(r1, c3.car, env, rowUnifier);
                    }
                }
                return new EnvSet(new UnifyException("Can't unify rows: " + typeToString(r1, env) + " and " + typeToString(r2, env)));
            }
            String name = ((StringAtom) c0.car).value;
            Cons c1 = (Cons) c0.cdr;
            Cons c2 = (Cons) c1.cdr;
            if(name.equals("from"))
            {
                return unifyRows(c2.car, r2, env, new FromUnifier(rowUnifier, c1.car));
            }
            else if(name.equals("to"))
            {
                return unifyRows(c2.car, r2, env, new ToUnifier(rowUnifier, c1.car));
            }
            else
            {
                Cons c3 = (Cons) c2.cdr;
                return unifyRows(c1.car, c2.car, c3.car, r2, env, rowUnifier);
            }
        }
    }

    private static interface IRowTypeUnifier
    {
        EnvSet unifyTypes(ISExp t1, ISExp t2, Environment env);
    }

    private static enum DefaultUnifier implements IRowTypeUnifier
    {
        INSTANCE;

        @Override
        public EnvSet unifyTypes(ISExp t1, ISExp t2, Environment env)
        {
            return unifyCheck(t1, t2, env);
        }
    }

    private static class FromUnifier implements IRowTypeUnifier
    {
        private final IRowTypeUnifier parent;
        private final ISExp t;

        private FromUnifier(IRowTypeUnifier parent, ISExp t)
        {
            this.parent = parent;
            this.t = t;
        }

        @Override
        public EnvSet unifyTypes(ISExp t1, ISExp t2, Environment env)
        {
            return parent.unifyTypes(absType(lift(t), t1), t2, env);
        }
    }

    private static class ToUnifier implements IRowTypeUnifier
    {
        private final IRowTypeUnifier parent;
        private final ISExp t;

        private ToUnifier(IRowTypeUnifier parent, ISExp t)
        {
            this.parent = parent;
            this.t = t;
        }

        @Override
        public EnvSet unifyTypes(ISExp t1, ISExp t2, Environment env)
        {
            return parent.unifyTypes(absType(lift(t1), t), t2, env);
        }
    }

    private EnvSet unifyRows(ISExp l, ISExp t, ISExp r, ISExp s, Environment env, IRowTypeUnifier rowUnifier)
    {
        ImmutableSet.Builder<Environment> builder = ImmutableSet.builder();
        ImmutableSet<Pair<Environment, ISExp>> inserters = null;
        try
        {
            inserters = insert(l, t, s, env, rowUnifier);
        }
        catch (UnifyException e)
        {
            return new EnvSet(e);
        }
        UnifyException exception = null;
        for (final Pair<Environment, ISExp> inserter : inserters)
        {
            ISExp s_l = inserter.getRight();
            try
            {
                Environment newEnv = env.mergeWith(inserter.getLeft());
                EnvSet unifiers = unifyRows(r, s_l, newEnv, rowUnifier);
                builder.addAll(unifiers.evs);
            }
            catch (UnifyException e)
            {
                exception = e;
            }
        }
        ImmutableSet<Environment> evs = builder.build();
        return new EnvSet(evs, exception);
    }

    // mostly done?
    private ImmutableSet<Pair<Environment, ISExp>> insert(final ISExp l, final ISExp t, ISExp r, Environment env, IRowTypeUnifier rowUnifier) throws UnifyException
    {
        ISExp rLink = env.find(r);
        if(rLink instanceof Symbol)
        {
            final Symbol A = (Symbol) rLink;
            final ISExp B = getFreshRow();
            ISExp t2 = getFreshVar();
            if(occurs(A, t, env))
            {
                return ImmutableSet.of();
            }
            final ISExp ra = makeRow(l, t2, B);
            EnvSet envSet = rowUnifier.unifyTypes(t2, t, env);
            if(envSet.evs.isEmpty())
            {
                throw envSet.exception;
            }
            ImmutableSet.Builder<Pair<Environment, ISExp>> builder = ImmutableSet.builder();
            Environment sub = Environment.fromSub(A, ra);
            for (Environment e : envSet.evs)
            {
                builder.add(Pair.of(e.mergeWith(sub), B));
            }
            return builder.build();
        }
        if(length(rLink) == 1)
        {
            throw new UnifyException("Can't insert label " + l + " into an empty row");
        }
        Cons c0 = (Cons) rLink;
        Cons c1 = (Cons) c0.cdr;
        Cons c2 = (Cons) c1.cdr;
        String name = ((StringAtom) c0.car).value;
        if(name.equals("from"))
        {
            return insert(l, t, c2.car, env, new FromUnifier(rowUnifier, c1.car));
        }
        else if(name.equals("to"))
        {
            return insert(l, t, c2.car, env, new ToUnifier(rowUnifier, c1.car));
        }
        Cons c3 = (Cons) c2.cdr;
        ISExp l1 = c1.car;
        ISExp t1 = c2.car;
        ISExp r1 = c3.car;
        ISExp label1 = env.find(l);
        ISExp label2 = env.find(l1);
        ImmutableSet<Pair<Environment, ISExp>> inserters;
        UnifyException exception = null;
        // TODO: check if this is the only case in which we should stop
        // FIXME: ["#label", "#T"]
        if(!(label1 instanceof Symbol) && label1.equals(label2))
        {
            inserters = ImmutableSet.of();
        }
        else try
        {
            inserters = insert(l, t, r1, env, rowUnifier);
        }
        catch(UnifyException e)
        {
            inserters = ImmutableSet.of();
            exception = e;
        }
        EnvSet fields;
        try
        {
            fields = unifyFields(l, l1, t, t1, env, rowUnifier);
        }
        catch(UnifyException e)
        {
            fields = new EnvSet(e);
        }
        ImmutableSet.Builder<Pair<Environment, ISExp>> builder = ImmutableSet.builder();
        for (Environment field : fields.evs)
        {
            builder.add(Pair.of(field, r1));
        }
        for (Pair<Environment, ISExp> inserter : inserters)
        {
            builder.add(Pair.of(inserter.getLeft(), makeRow(l1, t1, inserter.getRight())));
        }
        ImmutableSet<Pair<Environment, ISExp>> set = builder.build();
        if(set.isEmpty())
        {
            // FIXME: which of these should be thrown?
            if(exception != null)
            {
                throw exception;
            }
            throw fields.exception;
        }
        return set;
    }

    private EnvSet unifyFields(ISExp l1, ISExp l2, ISExp t1, ISExp t2, Environment env, IRowTypeUnifier rowUnifier) throws UnifyException
    {
        if(l1 instanceof Symbol)
        {
            // FIXME: eq?
            return rowUnifier.unifyTypes(t1, t2, env.mergeWith(Environment.fromSub((Symbol) l1, l2)));
        }
        if(l2 instanceof Symbol)
        {
            // FIXME: eq?
            return rowUnifier.unifyTypes(t1, t2, env.mergeWith(Environment.fromSub((Symbol) l2, l1)));
        }
        else if(l1.equals(l2)) // TODO: good enough equality?
        {
            return rowUnifier.unifyTypes(t1, t2, env);
        }
        throw new UnifyException("Couldn't unify labels: " + l1 + " and " + l2);
    }

    /*private ISExp insert(ISExp l, ISExp t, ISExp r)
    {
        ISExp rLink = find(r);
        if(rLink instanceof Symbol)
        {
            Symbol type = (Symbol) rLink;
            if(occurs(type, t))
            {
                ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
                throw new IllegalStateException("recursive map type: " + typeToString(type, typeMap) + " inside " + typeToString(t, typeMap));
            }
            ISExp r2 = getFreshVar();
            union(type, makeRow(l, t, r2));
            return r2;
        }
        else if(rLink instanceof Cons && ((Cons) rLink).car.equals(makeString("{}")))
        {
            if(length(rLink) == 4) // not {}
            {
                Cons c1 = (Cons) rLink;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;
                // TODO: polymorphic labels?
                ISExp l1Link = find(l);
                ISExp l2Link = find(c2.car);
                // FIXME: if label is a symbol, there should be a constraint added
                // inHead
                if(l1Link instanceof Symbol)
                {
                    union((Symbol) l1Link, l2Link);
                    unify(c3.car, t);
                }
                else if(l2Link instanceof Symbol)
                {
                    union((Symbol) l2Link, l1Link);
                    unify(c3.car, t);
                }
                else if(c2.car.equals(l))
                {
                    unify(c3.car, t);
                }
                else
                {
                    // inTail
                    return makeRow(c2.car, c3.car, insert(l, t, c4.car));
                }
                return c4.car;
            }
        }
        ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
        throw new IllegalStateException("Type error: can't insert " + typeToString(l, typeMap) + " : " + typeToString(t, typeMap) + " into " + typeToString(rLink, typeMap));
    }*/

    private static boolean occurs(Symbol type, ISExp other, Environment env)
    {
        ISExp link = env.find(other);
        if(link instanceof Symbol)
        {
            // FIXME: ==?
            return type.equals(link);
        }
        else if(link instanceof IList)
        {
            IList list = (IList) link;
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                if(occurs(type, cons.car, env))
                {
                    return true;
                }
                list = cons.cdr;
            }
        }
        return false;
    }

    private static EnvSet union(final Symbol type, final ISExp other, Environment env) throws UnifyException
    {
        if(!type.equals(other))
        {
            if(!occurs(type, other, env))
            {
                return new EnvSet(env.mergeWith(Environment.fromSub(type, other)));
                // manually creating it cause it might be complex
                /*union.makeSet(other);
                union.union(type, other);
                // TODO: check
                boundVars.put(type, other);*/
            }
            return new EnvSet(new UnifyException("Occurs: " + typeToString(type, env) + " in " + typeToString(other, env)));
            //ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
            //throw new IllegalStateException("recursive type: " + typeToString(type, typeMap) + " = " + typeToString(other, typeMap));
        }
        return new EnvSet(env);
    }

    /*public boolean isBound(ISExp symbol)
    {
        // TODO: check
        return boundVars.containsKey(symbol);
    }*/

    /*public ImmutableMap<ISExp, ISExp> buildTypeMap()
    {
        ImmutableMap<ISExp, ImmutableSet<ISExp>> map = union.toMap();
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        for (ISExp from : map.keySet())
        {
            ISExp to = null;
            // can be faster since values have a lot of repeats
            for(ISExp candidate : map.get(from))
            {
                if(!(candidate instanceof Symbol))
                {
                    to = candidate;
                    break;
                }
            }
            if(to != null)
            {
                builder.put(from, to);
            }
        }
        return builder.build();
    }*/

    /*public ImmutableMap<Symbol, ISExp> buildResult()
    {
        ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
        ImmutableMap.Builder<Symbol, ISExp> builder = ImmutableMap.builder();
        for(java.util.Map.Entry<Symbol, ISExp> entry : globalEnv.entrySet())
        {
            ISExp type = entry.getValue();
            if(typeMap.containsKey(type))
            {
                type = typeMap.get(type);
            }
            builder.put(entry.getKey(), type);
        }
        return builder.build();
    }*/

    public String typeToString(ISExp type)
    {
        return typeToString(type, globalEnv);
    }

    private static String rowToString(ISExp row, Environment env, boolean first, int ident)
    {
        row = env.find(row);
        String r = "\n" + StringUtils.repeat(' ', ident * 2);
        if(row instanceof Symbol)
        {
            if(!first)
            {
                r += "| ";
            }
            return r + typeToString(row, env, ident);
        }
        if(length(row) == 1) return "";
        Cons c1 = (Cons) row;
        Cons c2 = (Cons) c1.cdr;
        Cons c3 = (Cons) c2.cdr;
        Cons c4 = (Cons) c3.cdr;
        return r + typeToString(c2.car, env, ident) + " = " + typeToString(c3.car, env, ident) + rowToString(c4.car, env, false, ident);
    }

    public static String typeToString(ISExp type, Environment env)
    {
        return typeToString(type, env, 0);
    }

    public static String typeToString(ISExp type, Environment env, int ident)
    {
        type = env.find(type);
        if(type instanceof StringAtom)
        {
            return ((StringAtom) type).value;
        }
        else if(type instanceof Symbol)
        {
            return ((Symbol) type).value;
        }
        else if(type == Unbound.INSTANCE)
        {
            return "&unbound";
        }
        Cons cons = (Cons)type;
        if(cons.car.equals(makeString("{}")))
        {
            if(cons.cdr == Nil.INSTANCE)
            {
                return "{ }";
            }
            return "{" + rowToString(cons, env, true, ident + 1) + "\n" + StringUtils.repeat(' ', ident * 2) + "}";
        }
        else if(cons.car.equals(makeString("from")))
        {
            Cons c1 = (Cons) cons.cdr;
            Cons c2 = (Cons) c1.cdr;
            return "[from " + typeToString(c1.car, env, ident) + ", " + typeToString(c2.car, env, ident) + "]";
        }
        else if(cons.car.equals(makeString("to")))
        {
            Cons c1 = (Cons) cons.cdr;
            Cons c2 = (Cons) c1.cdr;
            return "[to " + typeToString(c1.car, env, ident) + ", " + typeToString(c2.car, env, ident) + "]";
        }
        else if(cons.car.equals(makeString("label")))
        {
            if(isStringLabelExp(cons))
            {
                return "\"" + sugarLabel(cons) + "\"";
            }
            return "[label: " + ((Cons) cons.cdr).car + "]";
        }
        else if(cons.car.equals(makeString("*")))
        {
            return "Prod " + typeToString(((Cons) cons.cdr).car, env, ident);
        }
        else if(cons.car.equals(makeString("+")))
        {
            return "Sum " + typeToString(((Cons) cons.cdr).car, env, ident);
        }
        else if(cons.car.equals(makeString("->")))
        {
            // FIXME
            /*if (length(type) == 2)
            {
                r = typeToString(cons.car, env) + " -> ";
                cons = (Cons) cons.cdr;
            }
            else
            {
                r = "(";
                while (cons.cdr != Nil.INSTANCE)
                {
                    r += typeToString(cons.car, env);
                    cons = (Cons) cons.cdr;
                    if (cons.cdr != Nil.INSTANCE)
                    {
                        r += ", ";
                    }
                }
                r += ") -> ";
            }
            if (cons.car instanceof IList)
            {
                r += "(" + typeToString(cons.car, env) + ")";
            }
            else
            {
                r += typeToString(cons.car, env);
            }*/
            Cons c1 = (Cons) cons.cdr;
            IList args = c1.cdr;
            String r = "((";
            while(args != Nil.INSTANCE)
            {
                Cons c2 = (Cons) args;
                r += typeToString(c2.car, env, ident);
                if(c2.cdr != Nil.INSTANCE)
                {
                    r += ", ";
                }
                args = c2.cdr;
            }
            r += ") -> " + typeToString(c1.car, env, ident) + ")";
            return r;
        }
        throw new UnsupportedOperationException("typeToString: " + type);
    }

    /*public void copyConstraints(ISExp type, ISExp newVar)
    {
        constraints.putAll(newVar, constraints.get(type));
    }*/
}
